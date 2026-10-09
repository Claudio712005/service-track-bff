package com.clau.service_track.bff.infra.client.catalogo

import com.clau.service_track.bff.infra.config.ServiceProperties
import java.time.Duration
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "servicetrack.catalogo")
data class CatalogoProperties(
    override val baseUrl: String,
    override val connectTimeout: Duration = Duration.ofSeconds(1),
    override val responseTimeout: Duration = Duration.ofSeconds(2),
) : ServiceProperties
