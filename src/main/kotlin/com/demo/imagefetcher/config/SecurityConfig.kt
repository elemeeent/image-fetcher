package com.demo.imagefetcher.config

import com.demo.imagefetcher.config.properties.UserProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService
import org.springframework.security.core.userdetails.ReactiveUserDetailsService
import org.springframework.security.core.userdetails.User
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.reactive.CorsConfigurationSource
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource

@Configuration
class SecurityConfig(
    val userProperties: UserProperties
) {

    // it should be done via normal auth service
    // current realization is just a demo
    @Bean
    fun userDetailsService(): ReactiveUserDetailsService {
        val user = User.withUsername(userProperties.login)
            .password(passwordEncoder().encode(userProperties.password))
            .roles(userProperties.role)
            .build()
        return MapReactiveUserDetailsService(user)
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder()
    }


    @Bean
    fun springSecurityFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        return http
            .csrf { it.disable() }
            .cors { }
            .httpBasic { }
            .formLogin { }

            .authorizeExchange {
                it
                    .pathMatchers("/cards/**").permitAll()
                    .pathMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                    .anyExchange().denyAll()
            }

            .build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOriginPatterns = listOf("http://localhost:*")
        configuration.allowedMethods = listOf("GET", "POST", "OPTIONS", "HEAD")
        configuration.allowedHeaders = listOf("Authorization", "Content-Type", "X-Requested-With")
        configuration.allowCredentials = true

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}