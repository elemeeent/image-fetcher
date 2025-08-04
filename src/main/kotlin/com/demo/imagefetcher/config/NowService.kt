package com.demo.imagefetcher.config

import org.springframework.context.annotation.Configuration
import java.time.Instant

@Configuration
class NowService {

    fun now(): Instant = Instant.now()
}