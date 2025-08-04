package com.demo.imagefetcher.controller

import com.demo.imagefetcher.model.dto.CardRequest
import com.demo.imagefetcher.model.dto.CardStatusResponse
import com.demo.imagefetcher.model.dto.OperationIdResponse
import com.demo.imagefetcher.service.OrchestratorService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono
import java.util.*

@RestController
@RequestMapping("/cards")
class CardController(
    private val orchestratorService: OrchestratorService
) {

    @Operation(
        summary = "Submit card names for image retrieval",
        description = "Starts async operation to fetch PNG URLs from Scryfall and returns operationId.",
        responses = [
            ApiResponse(responseCode = "200", description = "Operation started successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request format")
        ]
    )
    @PostMapping
    fun submit(@RequestBody @Valid request: CardRequest): Mono<OperationIdResponse> =
        orchestratorService.createOperation(request).map { OperationIdResponse(it.operationId) }


    @Operation(
        summary = "Check operation status by ID",
        description = "Returns operation status: PROCESSING, SUCCESS, PARTIAL_SUCCESS, or FAILURE. Also returns results and/or errors.",
        responses = [
            ApiResponse(responseCode = "200", description = "Status retrieved"),
            ApiResponse(responseCode = "404", description = "Operation not found")
        ]
    )
    @GetMapping("/{operationId}")
    fun status(@PathVariable @Valid operationId: UUID): Mono<CardStatusResponse> =
        orchestratorService.getOperationStatus(operationId)

}