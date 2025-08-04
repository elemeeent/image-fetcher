package com.demo.imagefetcher.service

import com.demo.imagefetcher.config.NowService
import com.demo.imagefetcher.model.dto.CardRequest
import com.demo.imagefetcher.model.dto.OperationIdResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.*

@Service
class OrchestratorService(
    private val cardService: CardService,
    private val operationService: OperationService,
    private val nowService: NowService
) {
    private val log = LoggerFactory.getLogger(OrchestratorService::class.java)

    /**
     * Creates a new operation for the given card request.
     *
     * Steps:
     * - Reads requested names from the input.
     * - Asks [CardService] to upsert and return cards in the same order.
     * - Collects their IDs and creates an operation in `PROCESSING` status.
     * - Starts a background refresh for those cards.
     * - When refresh is done, finalizes the operation status.
     *
     * This method returns the operation id right away. The refresh and
     * finalization run in the background and do not block the caller.
     *
     * @param cardRequest request that contains the list of card names
     * @return [Mono] that emits the [OperationIdResponse] with the new operation id
     */

    fun createOperation(cardRequest: CardRequest): Mono<OperationIdResponse> {
        val requestedNames = cardRequest.cardNames
        log.info("Creating new operation with cards: $requestedNames")

        return cardService.upsertAndGetCardsByRequestedNames(requestedNames)
            .flatMap { cards ->
                val idsInRequestOrder = cards.mapNotNull { it.id }.toSet()
                operationService.createOperationWithCardIds(requestedNames, idsInRequestOrder)
            }
            .flatMap { op ->
                val operationId = requireNotNull(op.id)
                cardService.refreshToTerminalManyByRequestedNames(requestedNames)
                    .then(operationService.finalizeOperationStatus(operationId, nowService.now()))
                    .subscribe(
                        { /* ok */ },
                        { ex ->
                            log.error("Background finalize failed operation id: $operationId. Reason: ${ex.message}")
                        }
                    )
                Mono.just(OperationIdResponse(operationId))
            }
    }

    /**
     * Returns the current status and results for the given operation.
     *
     * The method delegates to [OperationService], which loads the cards by
     * their IDs and builds an aggregated response.
     *
     * @param operationId id of the operation to read
     * @return [Mono] that emits the aggregated status and results
     */

    fun getOperationStatus(operationId: UUID) = operationService.getOperationStatus(operationId)
}
