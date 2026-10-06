package com.clau.service_track.bff.infra.config

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "servicetrack.resilience")
data class ResilienceProperties(
    val maxAttempts: Int = 3,
    val initialBackoff: Duration = Duration.ofMillis(200),
    val backoffMultiplier: Double = 2.0,
    val maxBackoff: Duration = Duration.ofSeconds(2),
    val slidingWindowSize: Int = 20,
    val minimumNumberOfCalls: Int = 10,
    val failureRateThreshold: Float = 50f,
    val waitDurationInOpenState: Duration = Duration.ofSeconds(10),
)
