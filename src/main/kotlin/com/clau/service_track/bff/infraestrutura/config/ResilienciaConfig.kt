package com.clau.service_track.bff.infraestrutura.config

import com.clau.service_track.bff.dominio.excecao.ServicoIndisponivelException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.core.IntervalFunction
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(ResilienciaProperties::class)
class ResilienciaConfig {

    @Bean
    fun retryDeUsuarios(propriedades: ResilienciaProperties): Retry = Retry.of(
        "usuarios",
        RetryConfig.custom<Any>()
            .maxAttempts(propriedades.tentativas)
            .intervalFunction(
                IntervalFunction.ofExponentialBackoff(
                    propriedades.esperaInicial,
                    propriedades.multiplicadorDaEspera,
                    propriedades.esperaMaxima,
                ),
            )
            .retryOnException { erro -> erro is ServicoIndisponivelException }
            .failAfterMaxAttempts(false)
            .build(),
    )

    @Bean
    fun disjuntorDeUsuarios(propriedades: ResilienciaProperties): CircuitBreaker = CircuitBreaker.of(
        "usuarios",
        CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(propriedades.janelaDoDisjuntor)
            .minimumNumberOfCalls(propriedades.chamadasMinimas)
            .failureRateThreshold(propriedades.limiteDeFalhaPercentual)
            .waitDurationInOpenState(propriedades.esperaAberto)
            .permittedNumberOfCallsInHalfOpenState(2)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .recordException { erro -> erro is ServicoIndisponivelException }
            .build(),
    )
}
