package com.clau.service_track.bff.infra.config

import com.clau.service_track.bff.domain.exception.ServicoIndisponivelException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig
import io.github.resilience4j.core.IntervalFunction
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig

class ResilienceFactory(private val properties: ResilienceProperties) {

    fun retry(name: String): Retry = Retry.of(
        name,
        RetryConfig.custom<Any>()
            .maxAttempts(properties.maxAttempts)
            .intervalFunction(
                IntervalFunction.ofExponentialBackoff(
                    properties.initialBackoff,
                    properties.backoffMultiplier,
                    properties.maxBackoff,
                ),
            )
            .retryOnException { error -> error is ServicoIndisponivelException }
            .failAfterMaxAttempts(false)
            .build(),
    )

    fun circuitBreaker(name: String): CircuitBreaker = CircuitBreaker.of(
        name,
        CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(properties.slidingWindowSize)
            .minimumNumberOfCalls(properties.minimumNumberOfCalls)
            .failureRateThreshold(properties.failureRateThreshold)
            .waitDurationInOpenState(properties.waitDurationInOpenState)
            .permittedNumberOfCallsInHalfOpenState(2)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .recordException { error -> error is ServicoIndisponivelException }
            .build(),
    )
}
