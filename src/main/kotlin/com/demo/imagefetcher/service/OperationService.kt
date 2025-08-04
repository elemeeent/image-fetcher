package com.demo.imagefetcher.service

import com.demo.imagefetcher.config.NowService
import com.demo.imagefetcher.exception.NotFoundException
import com.demo.imagefetcher.model.dto.CardFailureItem
import com.demo.imagefetcher.model.dto.CardResultItem
import com.demo.imagefetcher.model.dto.CardStatusResponse
import com.demo.imagefetcher.model.entity.card.CardEntity
import com.demo.imagefetcher.model.entity.card.CardStatus
import com.demo.imagefetcher.model.entity.operation.OperationEntity
import com.demo.imagefetcher.model.entity.operation.OperationStatus
import com.demo.imagefetcher.repository.OperationRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.*

@Service
class OperationService(
    private val operationRepository: OperationRepository,
    private val cardService: CardService,
    private val nowService: NowService,
    private val transactionalOperator: TransactionalOperator,
) {

    private val log = LoggerFactory.getLogger(OperationService::class.java)

    /**
     * Creates and persists a new operation that references a set of card UUIDs and
     * stores the corresponding user-requested names.
     *
     * The operation is initialized with `PROCESSING` status. The provided `requested`
     * and `cardIds` are converted to arrays and saved as part of the operation record.
     *
     * @param requested user-supplied names associated with the cards to be processed
     * @param cardIds identifiers of cards included in the operation
     * @return [Mono] emitting the persisted [OperationEntity]
     */

    fun createOperationWithCardIds(
        requested: Set<String>,
        cardIds: Set<UUID>
    ): Mono<OperationEntity> {
        log.info("Creating new operation with cards: $requested")
        val op = OperationEntity(
            status = OperationStatus.PROCESSING.name,
            cardIds = cardIds.toTypedArray(),
            requestedNames = requested.toTypedArray()
        )
        return operationRepository.save(op).`as`(transactionalOperator::transactional)
    }

    /**
     * Computes and persists the final status for the given operation.
     *
     * If the operation has no card identifiers, it is finalized as `FAILURE`. Otherwise,
     * the cards are loaded and the final status is determined from their terminal states
     * (`SUCCESS`, `PARTIAL_SUCCESS`, `FAILURE`, or `PROCESSING`). The chosen status is then
     * written to the operation along with `completedAt`.
     *
     * @param operationId operation identifier
     * @param completedAt timestamp to record as the completion time (defaults to [NowService.now])
     * @return [Mono] that completes when the status update has been persisted
     */

    fun finalizeOperationStatus(operationId: UUID, completedAt: Instant = nowService.now()): Mono<Void> {
        return getOperation(operationId).flatMap { op ->
            if (op.cardIds.isEmpty()) {
                // Нечего считать — считаем как FAILURE без результатов
                updateOperationStatus(operationId, OperationStatus.FAILURE, completedAt)
            } else {
                cardService.getCardsByIds(op.cardIds.toList())
                    .map { cards -> computeFinalStatus(op, cards) }
                    .flatMap { final ->
                        updateOperationStatus(operationId, final, completedAt)
                    }
            }
        }
    }

    /**
     * Returns the current aggregated status and results for the specified operation.
     *
     * If the operation contains no card identifiers, a `FAILURE` status with an error message
     * is produced. Otherwise, the current states of the referenced cards are loaded and a
     * [CardStatusResponse] is built, including successes, failures, and a derived status
     * (unless a non-`PROCESSING` status has already been persisted).
     *
     * @param operationId operation identifier
     * @return [Mono] emitting the aggregated [CardStatusResponse]
     */

    fun getOperationStatus(operationId: UUID): Mono<CardStatusResponse> {
        log.info("Requesting operation by id: $operationId")
        return getOperation(operationId)
            .switchIfEmpty(
                Mono.error(NotFoundException("No operation with id: $operationId"))
            )
            .flatMap { op ->
            if (op.cardIds.isEmpty()) {
                Mono.just(
                    CardStatusResponse(
                        operationId = op.id!!,
                        status = OperationStatus.FAILURE,
                        error = "Operation has no card ids"
                    )
                )
            } else {
                cardService.getCardsByIds(op.cardIds.toList())
                    .flatMap { cards -> buildResponse(op, cards) }
            }
        }
    }

    // private logic

    /**
     * Loads an operation by its identifier.
     *
     * @param operationId operation identifier
     * @return [Mono] emitting the [OperationEntity], if found
     */
    private fun getOperation(operationId: UUID): Mono<OperationEntity> =
        operationRepository.findById(operationId)

    /**
     * Persists the provided status and completion time for the operation.
     *
     * @param operationId operation identifier
     * @param status status to store on the operation
     * @param completedAt timestamp to store as the completion time
     * @return [Mono] that completes when the update has been applied
     */

    private fun updateOperationStatus(
        operationId: UUID,
        status: OperationStatus,
        completedAt: Instant
    ): Mono<Void> =
        operationRepository.updateStatus(operationId, status.name, completedAt)

    /**
     * Computes the final operation status from the terminal states of its cards.
     *
     * Counting logic:
     * - Increment success for cards in `SUCCESS`.
     * - Increment fail for cards in `FAILURE`.
     * - All other states are considered pending.
     *
     * Decision:
     * - success > 0 and fail == 0 → `SUCCESS`
     * - success > 0 and fail > 0  → `PARTIAL_SUCCESS`
     * - fail > 0 and success == 0 → `FAILURE`
     * - otherwise                  → `PROCESSING`
     *
     * @param operation operation containing the set of card IDs
     * @param cards card entities corresponding to those IDs
     * @return computed [OperationStatus]
     */
    private fun computeFinalStatus(operation: OperationEntity, cards: List<CardEntity>): OperationStatus {
        val idCardsMap = cards.associateBy { it.id!! }
        var success = 0
        var fail = 0
        operation.cardIds.forEach { id ->
            when (idCardsMap[id]?.status) {
                CardStatus.SUCCESS.name -> success++
                CardStatus.FAILURE.name -> fail++
                else -> { /* pending states */
                }
            }
        }
        return when {
            success > 0 && fail == 0 -> OperationStatus.SUCCESS
            success > 0 && fail > 0 -> OperationStatus.PARTIAL_SUCCESS
            fail > 0 && success == 0 -> OperationStatus.FAILURE
            else -> OperationStatus.PROCESSING
        }
    }

    /**
     * Builds a user-facing [CardStatusResponse] for the operation by aligning results
     * with the operation's card identifiers and summarizing successes and failures.
     *
     * If the persisted status is still `PROCESSING`, the status in the response is derived
     * from the current in-memory counts; otherwise, the persisted status is used as-is.
     *
     * @param operation operation with card identifiers and requested names
     * @param cards current state of all referenced cards
     * @return aggregated [CardStatusResponse] including results, failures, and status
     */
    private fun buildResponse(
        operation: OperationEntity,
        cards: List<CardEntity>
    ): Mono<CardStatusResponse> {
        val idCardsMap = cards.associateBy { it.id!! }
        val results = mutableListOf<CardResultItem>()
        val failures = mutableListOf<CardFailureItem>()
        var needProcessing = false

        operation.cardIds.forEachIndexed { _, id ->
            val card = idCardsMap[id]
            if (card == null) {
                needProcessing = true
            } else {
                when (card.status) {
                    CardStatus.SUCCESS.name -> {
                        val url = card.pngUrl
                        if (url != null) {
                            results.add(CardResultItem(card.requestedName, url))
                        } else {
                            failures.add(CardFailureItem(card.requestedName, "PNG URL missing"))
                        }
                    }
                    CardStatus.FAILURE.name ->
                        failures.add(CardFailureItem(card.requestedName, card.failReason ?: "Fetch failed"))
                    CardStatus.NEW.name, CardStatus.PROCESSING.name, CardStatus.STALE.name ->
                        needProcessing = true
                }
            }
        }

        val status = processOperationStatus(operation, needProcessing, results, failures)

        // Обновляем в БД ТОЛЬКО если статус изменился и он терминальный (чтобы не перебирать completed_at).
        val updateMono =
            if (operation.status != status.name && status != OperationStatus.PROCESSING) {
                operationRepository.updateStatus(
                    id = operation.id!!,
                    status = status.name,
                    completedAt = nowService.now()
                )
            } else {
                Mono.empty()
            }

        return updateMono.then(
            Mono.just(
                generateCardResponse(
                    status = status,
                    operation = operation,
                    results = results,
                    failures = failures
                )
            )
        )
    }

    /**
     * Calculate operation status based on params
     *
     * @param needProcessing flag about current card fetching state
     * @param operation operation with card identifiers and requested names
     * @param results list if success cards results
     * @param failures list of failed cards results
     *
     * @return [OperationStatus] enum of current operation status based on all operation's cards
     */
    private fun processOperationStatus(
        operation: OperationEntity,
        needProcessing: Boolean,
        results: MutableList<CardResultItem>,
        failures: MutableList<CardFailureItem>
    ): OperationStatus = if (OperationStatus.valueOf(operation.status) == OperationStatus.PROCESSING) {
        when {
            needProcessing -> OperationStatus.PROCESSING
            results.isNotEmpty() && failures.isEmpty() -> OperationStatus.SUCCESS
            results.isNotEmpty() && failures.isNotEmpty() -> OperationStatus.PARTIAL_SUCCESS
            else -> OperationStatus.FAILURE
        }
    } else {
        OperationStatus.valueOf(operation.status)
    }

    /**
     * Generate final [CardStatusResponse] response and fill it with data
     *
     * @param status calculated operation status
     * @param operation operation with card identifiers and requested names
     * @param results list if success cards results
     * @param failures list of failed cards results
     *
     * @return [CardStatusResponse] response DTO related to API contract
     */
    private fun generateCardResponse(
        status: OperationStatus,
        operation: OperationEntity,
        results: MutableList<CardResultItem>,
        failures: MutableList<CardFailureItem>
    ): CardStatusResponse = when (status) {
        OperationStatus.PROCESSING ->
            CardStatusResponse(
                operationId = operation.id!!,
                status = OperationStatus.PROCESSING,
                results = results,
                failures = failures
            )

        OperationStatus.SUCCESS ->
            CardStatusResponse(
                operationId = operation.id!!,
                status = OperationStatus.SUCCESS,
                results = results
            )

        OperationStatus.PARTIAL_SUCCESS ->
            CardStatusResponse(
                operationId = operation.id!!,
                status = OperationStatus.PARTIAL_SUCCESS,
                results = results,
                failures = failures
            )

        OperationStatus.FAILURE ->
            CardStatusResponse(
                operationId = operation.id!!,
                status = OperationStatus.FAILURE,
                error = "All requests failed"
            )
    }
}
