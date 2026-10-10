package com.clau.service_track.bff.infra.config

import com.clau.service_track.bff.infra.client.catalogo.CatalogoProperties
import com.clau.service_track.bff.infra.client.ordens.OrdensProperties
import com.clau.service_track.bff.infra.client.usuarios.UsuariosProperties
import com.clau.service_track.bff.infra.web.filter.CabecalhosDeSaidaFilter
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
    fun webClientDeUsuarios(construtor: WebClient.Builder, properties: UsuariosProperties): WebClient =
        construir(construtor, properties)

    @Bean
    fun webClientDeCatalogo(construtor: WebClient.Builder, properties: CatalogoProperties): WebClient =
        construir(construtor, properties)

    @Bean
    fun webClientDeOrdens(construtor: WebClient.Builder, properties: OrdensProperties): WebClient =
        construir(construtor, properties)

    private fun construir(construtor: WebClient.Builder, properties: ServiceProperties): WebClient {
        val cliente = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.connectTimeout.toMillis().toInt())
            .responseTimeout(properties.responseTimeout)
            .doOnConnected { conexao ->
                conexao.addHandlerLast(
                    ReadTimeoutHandler(properties.responseTimeout.toMillis(), TimeUnit.MILLISECONDS),
                )
            }

        return construtor
            .baseUrl(properties.baseUrl)
            .clientConnector(ReactorClientHttpConnector(cliente))
            .filter(CabecalhosDeSaidaFilter())
            .build()
    }
}
