package com.demo.imagefetcher.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.scheduler")
data class SchedulerProperties(
    val batchSize: Int,
    val maxConcurrency: Int,
    val expireDays: Int
)