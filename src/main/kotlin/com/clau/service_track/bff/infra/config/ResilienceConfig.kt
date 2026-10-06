package com.clau.service_track.bff.infra.config

import com.clau.service_track.bff.domain.exception.ServicoIndisponivelException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.core.IntervalFunction
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(ResilienceProperties::class)
class ResilienceConfig {

    @Bean
    fun retryDeUsuarios(properties: ResilienceProperties): Retry = Retry.of(
        "usuarios",
        RetryConfig.custom<Any>()
            .maxAttempts(properties.maxAttempts)
            .intervalFunction(
                IntervalFunction.ofExponentialBackoff(
                    properties.initialBackoff,
                    properties.backoffMultiplier,
                    properties.maxBackoff,
                ),
            )
            .retryOnException { erro -> erro is ServicoIndisponivelException }
            .failAfterMaxAttempts(false)
            .build(),
    )

    @Bean
    fun disjuntorDeUsuarios(properties: ResilienceProperties): CircuitBreaker = CircuitBreaker.of(
        "usuarios",
        CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(properties.slidingWindowSize)
            .minimumNumberOfCalls(properties.minimumNumberOfCalls)
            .failureRateThreshold(properties.failureRateThreshold)
            .waitDurationInOpenState(properties.waitDurationInOpenState)
            .permittedNumberOfCallsInHalfOpenState(2)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .recordException { erro -> erro is ServicoIndisponivelException }
            .build(),
    )
}
