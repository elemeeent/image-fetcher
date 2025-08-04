package com.demo.imagefetcher.model.dto

import com.demo.imagefetcher.model.entity.operation.OperationStatus
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import java.util.*

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
data class CardStatusResponse(
    val operationId: UUID,
    val status: OperationStatus,
    val results: List<CardResultItem> = emptyList(),
    val failures: List<CardFailureItem> = emptyList(),
    val error: String? = null
)

data class CardResultItem(
    val cardName: String,
    val pngUrl: String
)

data class CardFailureItem(
    val cardName: String,
    val error: String? = null,
)
