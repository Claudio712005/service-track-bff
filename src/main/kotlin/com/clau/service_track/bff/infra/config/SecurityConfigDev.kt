package com.clau.service_track.bff.infra.config

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.web.server.SecurityWebFilterChain

@Configuration
@EnableWebFluxSecurity
@ConditionalOnProperty(prefix = "servicetrack.security.jwt", name = ["enabled"], havingValue = "false")
class SecurityConfigDev {

    private val log = LoggerFactory.getLogger(SecurityConfigDev::class.java)

    init {
        log.warn(
            "validacao de JWT DESLIGADA (servicetrack.security.jwt.enabled=false). " +
                "Toda rota esta aberta. Nunca subir assim em ambiente compartilhado."
        )
    }

    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain = http
        .csrf { it.disable() }
        .httpBasic { it.disable() }
        .formLogin { it.disable() }
        .authorizeExchange { troca -> troca.anyExchange().permitAll() }
        .build()
}
