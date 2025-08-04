package com.demo.imagefetcher.client.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class ImageUris(
    val png: String? = null,
)