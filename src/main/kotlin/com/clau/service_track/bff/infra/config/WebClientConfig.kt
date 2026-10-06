package com.clau.service_track.bff.infra.config

import com.clau.service_track.bff.infra.client.usuarios.CorrelationPropagationFilter
import com.clau.service_track.bff.infra.client.usuarios.UsuariosProperties
import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import java.util.concurrent.TimeUnit
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient

@Configuration
@Profile("!dev")
@EnableConfigurationProperties(UsuariosProperties::class)
class WebClientConfig {

    @Bean
    fun webClientDeUsuarios(
        construtor: WebClient.Builder,
        properties: UsuariosProperties,
    ): WebClient {
        val cliente = HttpClient.create()
            .option(
                ChannelOption.CONNECT_TIMEOUT_MILLIS,
                properties.connectTimeout.toMillis().toInt(),
            )
            .responseTimeout(properties.responseTimeout)
            .doOnConnected { conexao ->
                conexao.addHandlerLast(
                    ReadTimeoutHandler(properties.responseTimeout.toMillis(), TimeUnit.MILLISECONDS),
                )
            }

        return construtor
            .baseUrl(properties.baseUrl)
            .clientConnector(ReactorClientHttpConnector(cliente))
            .filter(CorrelationPropagationFilter())
            .build()
    }
}
