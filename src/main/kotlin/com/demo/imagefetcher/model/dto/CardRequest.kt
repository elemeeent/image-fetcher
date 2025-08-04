package com.demo.imagefetcher.model.dto

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.validation.annotation.Validated

@Validated
data class CardRequest(
    @field:NotNull
    @field:Size(min=1, max=100)
    val cardNames: Set<String>
)
