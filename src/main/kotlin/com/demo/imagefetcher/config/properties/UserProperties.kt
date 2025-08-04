package com.demo.imagefetcher.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.user")
data class UserProperties(
    val login: String,
    val password: String,
    val role: String
)
