package com.demo.imagefetcher.config

import io.r2dbc.spi.ConnectionFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration
import org.springframework.r2dbc.connection.R2dbcTransactionManager
import org.springframework.transaction.ReactiveTransactionManager
import org.springframework.transaction.reactive.TransactionalOperator


@Configuration
class R2dbcConfig(
    private val connectionFactory: ConnectionFactory
) : AbstractR2dbcConfiguration() {

    override fun connectionFactory() = connectionFactory

    @Bean
    fun reactiveTransactionManager(): ReactiveTransactionManager {
        return R2dbcTransactionManager(connectionFactory())
    }

    @Bean
    fun transactionalOperator(): TransactionalOperator {
        return TransactionalOperator.create(reactiveTransactionManager())
    }

}