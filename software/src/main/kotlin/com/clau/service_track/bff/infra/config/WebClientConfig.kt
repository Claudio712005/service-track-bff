package com.clau.service_track.bff.infra.config

import com.clau.service_track.bff.infra.client.catalogo.CatalogoProperties
import com.clau.service_track.bff.infra.client.ordens.OrdensProperties
import com.clau.service_track.bff.infra.client.usuarios.UsuariosProperties
import com.clau.service_track.bff.infra.web.filter.OutboundHeadersFilter
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
@EnableConfigurationProperties(
    UsuariosProperties::class,
    CatalogoProperties::class,
    OrdensProperties::class,
)
class WebClientConfig {

    @Bean
    fun usuariosWebClient(builder: WebClient.Builder, properties: UsuariosProperties): WebClient =
        webClient(builder, properties)

    @Bean
    fun catalogoWebClient(builder: WebClient.Builder, properties: CatalogoProperties): WebClient =
        webClient(builder, properties)

    @Bean
    fun ordensWebClient(builder: WebClient.Builder, properties: OrdensProperties): WebClient =
        webClient(builder, properties)

    private fun webClient(builder: WebClient.Builder, properties: ServiceProperties): WebClient {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.connectTimeout.toMillis().toInt())
            .responseTimeout(properties.responseTimeout)
            .doOnConnected { connection ->
                connection.addHandlerLast(
                    ReadTimeoutHandler(properties.responseTimeout.toMillis(), TimeUnit.MILLISECONDS),
                )
            }

        return builder
            .baseUrl(properties.baseUrl)
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .filter(OutboundHeadersFilter())
            .build()
    }
}
