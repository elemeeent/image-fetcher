package com.demo.imagefetcher.client.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class ScryfallError(
    @field:JsonProperty("object")
    val objectType: String? = null,
    val code: String? = null,
    val status: Int? = null,
    val warnings: List<String>? = listOf(),
    val details: String? = null,
)