package com.demo.imagefetcher.service

import com.demo.imagefetcher.client.ScryfallClient
import com.demo.imagefetcher.config.NowService
import com.demo.imagefetcher.config.properties.AppProperties
import com.demo.imagefetcher.model.entity.card.CardEntity
import com.demo.imagefetcher.model.entity.card.CardStatus
import com.demo.imagefetcher.repository.CardRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

@Service
class CardService(
    private val cardRepository: CardRepository,
    private val scryfallClient: ScryfallClient,
    private val nowService: NowService,
    private val appProperties: AppProperties,
    private val transactionalOperator: TransactionalOperator
) {

    private val log = LoggerFactory.getLogger(CardService::class.java)

    /**
     * Converts an arbitrary user-supplied name to its canonical form.
     *
     * Canonicalization trims leading/trailing whitespace and lowercases the value.
     * The canonical form is used as the unique key in the database.
     *
     * @param name raw user input
     * @return canonical name (trimmed and lowercased)
     */
    private fun toCanonical(name: String): String = name.trim().lowercase()

    /**
     * Ensures cards exist for the given set of requested names and returns them.
     *
     * For each requested name:
     * - Canonicalizes it and attempts to find an existing card by canonical name.
     * - If none exists, inserts a `NEW` row (upsert) with the initial requested name.
     * - If an existing card is `SUCCESS` but expired by TTL, marks it as `STALE`.
     *
     * The stream uses `flatMapSequential` to preserve the input order while allowing bounded
     * concurrency as configured by `appProperties.concurrency`.
     *
     * @param requestedNames set of user-supplied names (may differ in case/spacing)
     * @return a [Mono] emitting the list of [CardEntity] in the same order as the input
     */
    fun upsertAndGetCardsByRequestedNames(requestedNames: Set<String>): Mono<List<CardEntity>> {
        log.info("Upserting and fetching cards for the next names: $requestedNames")
        val now = nowService.now()
        val expireAt = now.minus(appProperties.imageCache.toLong(), ChronoUnit.DAYS)

        return Flux.fromIterable(requestedNames)
            .flatMapSequential({ requested ->
                val canonical = toCanonical(requested)
                cardRepository.findByCanonicalName(canonical)
                    .switchIfEmpty(
                        cardRepository.upsert(
                            canonicalName = canonical,
                            requestedName = requested.ifBlank { canonical },
                            pngUrl = null,
                            status = CardStatus.NEW.name,
                            fetchedAt = null,
                            failReason = null
                        ).`as`(transactionalOperator::transactional)
                    )
                    .flatMap { card ->
                        if (isCardStaled(card, expireAt)) {
                            log.info("Card ${card.id} is expired.")
                            cardRepository.upsert(
                                canonicalName = card.canonicalName,
                                requestedName = card.requestedName,
                                pngUrl = card.pngUrl,
                                status = CardStatus.STALE.name,
                                fetchedAt = card.fetchedAt,
                                failReason = card.failReason
                            ).`as`(transactionalOperator::transactional)
                        } else {
                            Mono.just(card)
                        }
                    }
            }, /* concurrency = */
                appProperties.concurrency)
            .collectList()
    }

    /**
     * Fetches cards by their database identifiers.
     *
     * @param ids list of card UUIDs to load
     * @return a [Mono] emitting all found [CardEntity] for the provided ids
     */
    fun getCardsByIds(ids: List<UUID>): Mono<List<CardEntity>> =
        cardRepository.findByIds(ids).collectList()

    /**
     * Refreshes many cards, identified by requested names, to a terminal state.
     *
     * Each requested name is canonicalized; the refresh then proceeds by canonical name.
     * The method preserves input order using `flatMapSequential` and applies bounded concurrency
     * from `appProperties.concurrency`.
     *
     * @param requestedNames set of user-supplied names to refresh
     * @return a [Mono] emitting the list of refreshed [CardEntity]
     */
    fun refreshToTerminalManyByRequestedNames(requestedNames: Set<String>): Mono<List<CardEntity>> =
        Flux.fromIterable(requestedNames)
            .flatMapSequential(
                { requested -> refreshToTerminal( toCanonical(requested)) },
                appProperties.concurrency
            )
            .collectList()

    /**
     * Refreshes a single card (by canonical name) to a terminal state (`SUCCESS` or `FAILURE`).
     *
     * Behavior:
     * - If the card is `SUCCESS` and not expired (within TTL), returns it immediately.
     * - Otherwise attempts to become the "leader" by atomically setting `PROCESSING` via
     *   `tryMarkProcessing`. The leader queries Scryfall and writes `SUCCESS`/`FAILURE`.
     * - Non-leaders poll the card row with a small delay until a terminal state appears.
     *
     * Errors:
     * - On Scryfall or processing errors, writes `FAILURE` with the error message.
     *
     * @param canonicalName canonical (normalized) card name used as the DB key and Scryfall query
     * @return a [Mono] emitting the resulting [CardEntity] in a terminal state
     */
    private fun refreshToTerminal(canonicalName: String): Mono<CardEntity> {
        val expireAt = nowService.now()
            .minus(appProperties.imageCache.toLong(), ChronoUnit.DAYS)

        fun isTerminal(status: String) =
            status == CardStatus.SUCCESS.name || status == CardStatus.FAILURE.name

        fun loopWait(): Mono<CardEntity> =
            cardRepository.findByCanonicalName(canonicalName).flatMap { card ->
                if (isTerminal(card.status)) Mono.just(card)
                else Mono.delay(Duration.ofMillis(200)).then(loopWait())
            }

        return cardRepository.findByCanonicalName(canonicalName)
            .flatMap { card ->
                if (!isCardStaled(card, expireAt)) {
                    Mono.just(card)
                } else {
                    cardRepository.tryMarkProcessing(canonicalName)
                        .hasElement()
                        .flatMap { exists ->
                            if (exists) {
                                fetchAndSave(canonicalName, card, nowService.now())
                            } else {
                                loopWait()
                            }
                        }
                }
            }
    }

    fun fetchAndSave(
        canonicalName: String,
        card: CardEntity,
        now: Instant
    ): Mono<CardEntity> {
        return scryfallClient.fetchCard(canonicalName)
            .flatMap { scryfallCard ->
                cardRepository.upsert(
                    canonicalName = canonicalName,
                    requestedName = card.requestedName,
                    pngUrl = scryfallCard.imageUris!!.png,
                    status = CardStatus.SUCCESS.name,
                    fetchedAt = now,
                    failReason = null
                ).`as`(transactionalOperator::transactional)
            }
            .onErrorResume { ex ->
                log.warn("Refresh failed for $canonicalName: ${ex.message}")
                cardRepository.upsert(
                    canonicalName = canonicalName,
                    requestedName = card.requestedName,
                    pngUrl = null,
                    status = CardStatus.FAILURE.name,
                    fetchedAt = null,
                    failReason = ex.message ?: "Fetch failed"
                ).`as`(transactionalOperator::transactional)
            }
    }

    fun fetchAndSave(
        card: CardEntity,
        now: Instant
    ): Mono<CardEntity> {
        return scryfallClient.fetchCard(card.canonicalName)
            .flatMap { scryfallCard ->
                cardRepository.upsert(
                    canonicalName = card.canonicalName,
                    requestedName = card.requestedName,
                    pngUrl = scryfallCard.imageUris!!.png,
                    status = CardStatus.SUCCESS.name,
                    fetchedAt = now,
                    failReason = null
                ).`as`(transactionalOperator::transactional)
            }
            .onErrorResume { ex ->
                log.warn("Refresh failed for card.canonicalName: ${ex.message}")
                cardRepository.upsert(
                    canonicalName = card.canonicalName,
                    requestedName = card.requestedName,
                    pngUrl = null,
                    status = CardStatus.FAILURE.name,
                    fetchedAt = null,
                    failReason = ex.message ?: "Fetch failed"
                ).`as`(transactionalOperator::transactional)
            }
    }

    /**
     * Determines whether a card with `SUCCESS` status is considered expired by TTL.
     *
     * @param card the card row to check
     * @param expireAt the cutoff instant; if `fetchedAt` is before this, the card is stale
     * @return `true` when the card is `SUCCESS` and its `fetchedAt` is before `expireAt`
     */
    private fun isCardStaled(card: CardEntity, expireAt: Instant): Boolean =
        card.status == CardStatus.SUCCESS.name && (card.fetchedAt?.isBefore(expireAt) == true)

}
