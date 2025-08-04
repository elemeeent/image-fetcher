package com.demo.imagefetcher.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.config")
data class AppProperties(
    val imageCache: Int,
    val concurrency: Int
)