package com.clau.service_track.bff.infraestrutura.config

import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "servicetrack.resiliencia")
data class ResilienciaProperties(
    val tentativas: Int = 3,
    val esperaInicial: Duration = Duration.ofMillis(200),
    val multiplicadorDaEspera: Double = 2.0,
    val esperaMaxima: Duration = Duration.ofSeconds(2),
    val janelaDoDisjuntor: Int = 20,
    val chamadasMinimas: Int = 10,
    val limiteDeFalhaPercentual: Float = 50f,
    val esperaAberto: Duration = Duration.ofSeconds(10),
)
