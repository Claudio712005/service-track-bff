package com.clau.service_track.bff.infra.config

import java.security.interfaces.RSAPublicKey
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.core.io.ResourceLoader
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.converter.RsaKeyConverters
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder
import org.springframework.security.web.server.SecurityWebFilterChain

@Configuration
@EnableWebFluxSecurity
@EnableConfigurationProperties(JwtProperties::class)
@ConditionalOnProperty(
    prefix = "servicetrack.security.jwt",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class SecurityConfig(
    private val propriedades: JwtProperties,
    private val resourceLoader: ResourceLoader,
) {

    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain = http
        .csrf { it.disable() }
        .httpBasic { it.disable() }
        .formLogin { it.disable() }
        .authorizeExchange { troca ->
            troca.pathMatchers(HttpMethod.OPTIONS).permitAll()
            troca.pathMatchers(*ROTAS_ABERTAS).permitAll()
            troca.anyExchange().authenticated()
        }
        .oauth2ResourceServer { oauth2 -> oauth2.jwt { } }
        .build()

    @Bean
    fun jwtDecoder(): ReactiveJwtDecoder {
        val recurso = resourceLoader.getResource(propriedades.publicKey)
        require(recurso.exists()) {
            "Chave pública JWT não encontrada em '${propriedades.publicKey}'. " +
                "Aponte servicetrack.security.jwt.public-key para o PEM emitido junto com a Lambda, " +
                "ou desligue a validação com servicetrack.security.jwt.enabled=false fora de produção."
        }

        val chave: RSAPublicKey = recurso.inputStream.use { RsaKeyConverters.x509().convert(it) }
            ?: error("Conteúdo de '${propriedades.publicKey}' não é uma chave pública X.509 válida")

        return NimbusReactiveJwtDecoder.withPublicKey(chave).build()
    }

    private companion object {
        val ROTAS_ABERTAS = arrayOf(
            "/actuator/health/**",
            "/actuator/info",
            "/actuator/prometheus",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/webjars/**",
        )
    }
}
