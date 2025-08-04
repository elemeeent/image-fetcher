package com.demo.imagefetcher.client

import com.demo.imagefetcher.client.model.ScryfallCard
import com.demo.imagefetcher.client.model.ScryfallError
import com.demo.imagefetcher.config.properties.ScryfallProperties
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClient
import java.time.Duration

@Component
class ScryfallClient(
    scryfallProperties: ScryfallProperties,
) {
    private val webClient: WebClient = WebClient.builder()
        .baseUrl(scryfallProperties.baseUrl)
        .clientConnector(
            ReactorClientHttpConnector(
                HttpClient.create()
                    .responseTimeout(Duration.ofSeconds(30))
            )
        )
        .build()

    fun fetchCard(name: String): Mono<ScryfallCard> {
        return if (name.isBlank()) {
            Mono.error(IllegalArgumentException("Card name cannot be blank"))
        } else {
            requestCardByName(name)
                .onStatus({ it.is4xxClientError || it.is5xxServerError }) {
                    processCardOnError(it)
                }
                .bodyToMono(ScryfallCard::class.java)
        }
    }

    private fun processCardOnError(response: ClientResponse?): Mono<Throwable?> =
        response!!.bodyToMono(ScryfallError::class.java)
            .defaultIfEmpty(
                ScryfallError(
                    objectType = "error",
                    code = "unknown",
                    status = response.statusCode().value(),
                    warnings = listOf(),
                    details = "Unknown error"
                )
            )
            .flatMap { Mono.error(RuntimeException(it.details)) }

    private fun requestCardByName(name: String): WebClient.ResponseSpec =
        webClient.get()
            .uri { uriBuilder ->
                uriBuilder.path("/cards/named")
                    .queryParam("fuzzy", name)
                    .build()
            }
            .retrieve()

}