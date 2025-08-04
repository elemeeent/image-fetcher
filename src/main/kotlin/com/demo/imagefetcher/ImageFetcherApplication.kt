package com.demo.imagefetcher

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication()
@ConfigurationPropertiesScan("com.demo.imagefetcher.config.properties")
@EnableScheduling
class ImageFetcherApplication

fun main(args: Array<String>) {
    runApplication<ImageFetcherApplication>(*args)
}
