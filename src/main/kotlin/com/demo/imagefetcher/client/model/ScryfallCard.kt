package com.demo.imagefetcher.client.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*

@JsonIgnoreProperties(ignoreUnknown = true)
data class ScryfallCard(
    val id: UUID,
    val name: String,
    @field:JsonProperty("image_uris")
    val imageUris: ImageUris? = null
)