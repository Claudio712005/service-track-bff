package com.clau.service_track.bff.infra.usuarios

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "servicetrack.usuarios")
data class UsuariosProperties(
    val baseUrl: String,
    val connectTimeout: Duration = Duration.ofSeconds(1),
    val responseTimeout: Duration = Duration.ofSeconds(2),
)
