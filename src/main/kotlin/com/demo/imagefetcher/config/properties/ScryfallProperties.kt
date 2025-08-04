package com.demo.imagefetcher.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "scryfall")
data class ScryfallProperties(
    val baseUrl: String,
)