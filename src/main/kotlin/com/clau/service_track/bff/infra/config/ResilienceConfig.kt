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
    fun fabricaDeResiliencia(properties: ResilienceProperties) = FabricaDeResiliencia(properties)

    @Bean
    fun retryDeUsuarios(fabrica: FabricaDeResiliencia): Retry = fabrica.retry("usuarios")

    @Bean
    fun disjuntorDeUsuarios(fabrica: FabricaDeResiliencia): CircuitBreaker = fabrica.circuitBreaker("usuarios")

    @Bean
    fun retryDeCatalogo(fabrica: FabricaDeResiliencia): Retry = fabrica.retry("catalogo")

    @Bean
    fun disjuntorDeCatalogo(fabrica: FabricaDeResiliencia): CircuitBreaker = fabrica.circuitBreaker("catalogo")

    @Bean
    fun retryDeOrdens(fabrica: FabricaDeResiliencia): Retry = fabrica.retry("ordens")

    @Bean
    fun disjuntorDeOrdens(fabrica: FabricaDeResiliencia): CircuitBreaker = fabrica.circuitBreaker("ordens")
}
