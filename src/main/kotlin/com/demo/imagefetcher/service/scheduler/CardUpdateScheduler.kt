package com.demo.imagefetcher.service.scheduler

import com.demo.imagefetcher.config.NowService
import com.demo.imagefetcher.config.properties.SchedulerProperties
import com.demo.imagefetcher.model.entity.card.CardStatus
import com.demo.imagefetcher.repository.CardRepository
import com.demo.imagefetcher.service.CardService
import com.demo.imagefetcher.service.FakeRedisService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.temporal.ChronoUnit

@Service
class CardUpdateScheduler(
    private val cardRepository: CardRepository,
    private val cardService: CardService,
    private val nowService: NowService,
    private val schedulerProperties: SchedulerProperties,
    private val fakeRedisService: FakeRedisService
) {
    private val log = LoggerFactory.getLogger(CardUpdateScheduler::class.java)

    @Scheduled(initialDelay = 5_000, fixedDelay = 5_000)
    fun refreshBatch() {
        log.info("Starting updating cards")
        if (fakeRedisService.tryLock("cardsUpdateProcess")) {
            try {
                val now = nowService.now()
                val pending = cardRepository.findByStatuses(
                    CardStatus.NEW.name,
                    CardStatus.STALE.name,
                    schedulerProperties.batchSize
                )
                val expired = cardRepository.findFetchedBefore(
                    CardStatus.SUCCESS.name,
                    now.minus(schedulerProperties.expireDays.toLong(), ChronoUnit.DAYS),
                    schedulerProperties.batchSize
                )

                Flux.merge(pending, expired)
                    .distinct { it.canonicalName }
                    .take(schedulerProperties.batchSize.toLong())
                    .flatMap(
                        { card ->
                            cardRepository.tryMarkProcessing(card.canonicalName)
                                .hasElement()
                                .flatMap { exists ->
                                    if (exists) {
                                        cardService.fetchAndSave(
                                            card = card,
                                            now = nowService.now()
                                        )
                                    } else {
                                        Mono.empty()
                                    }
                                }
                        },
                        schedulerProperties.maxConcurrency
                    )
                    .then()
                    .doOnSubscribe { log.info("Scheduler: refreshing up to ${schedulerProperties.batchSize} cards") }
                    .doOnError { ex -> log.error("Scheduler refresh failed: ${ex.message}", ex) }
                    .subscribe()
            } catch (ex: Exception) {
                log.error("Scheduler refresh failed. Reason: ${ex.message}", ex)
            } finally {
                fakeRedisService.unlock("cardsUpdateProcess")
            }
        } else {
            log.info("Another instance is already running this task")
        }
    }
}