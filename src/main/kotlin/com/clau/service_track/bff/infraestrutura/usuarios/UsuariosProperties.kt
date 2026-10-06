package com.clau.service_track.bff.infraestrutura.usuarios

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "servicetrack.usuarios")
data class UsuariosProperties(
    val baseUrl: String,
    val timeoutDeConexao: Duration = Duration.ofSeconds(1),
    val timeoutDeResposta: Duration = Duration.ofSeconds(2),
)
