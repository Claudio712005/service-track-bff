package com.clau.service_track.bff.infra.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "servicetrack.security.jwt")
data class JwtProperties(
    val enabled: Boolean = true,
    val publicKey: String = "classpath:publicKey.pem",
)
