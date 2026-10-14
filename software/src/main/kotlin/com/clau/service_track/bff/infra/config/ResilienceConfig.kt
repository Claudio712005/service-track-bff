package com.clau.service_track.bff.infra.config

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.retry.Retry
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(ResilienceProperties::class)
class ResilienceConfig {

    @Bean
    fun resilienceFactory(properties: ResilienceProperties) = ResilienceFactory(properties)

    @Bean
    fun usuariosRetry(factory: ResilienceFactory): Retry = factory.retry("usuarios")

    @Bean
    fun usuariosCircuitBreaker(factory: ResilienceFactory): CircuitBreaker = factory.circuitBreaker("usuarios")

    @Bean
    fun catalogoRetry(factory: ResilienceFactory): Retry = factory.retry("catalogo")

    @Bean
    fun catalogoCircuitBreaker(factory: ResilienceFactory): CircuitBreaker = factory.circuitBreaker("catalogo")

    @Bean
    fun ordensRetry(factory: ResilienceFactory): Retry = factory.retry("ordens")

    @Bean
    fun ordensCircuitBreaker(factory: ResilienceFactory): CircuitBreaker = factory.circuitBreaker("ordens")
}
