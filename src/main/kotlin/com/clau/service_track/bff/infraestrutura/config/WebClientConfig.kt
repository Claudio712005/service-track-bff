package com.clau.service_track.bff.infraestrutura.config

import com.clau.service_track.bff.infraestrutura.usuarios.UsuariosProperties
import io.netty.channel.ChannelOption
import io.netty.handler.timeout.ReadTimeoutHandler
import java.util.concurrent.TimeUnit
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient

@Configuration
@EnableConfigurationProperties(UsuariosProperties::class)
class WebClientConfig {

    @Bean
    fun webClientDeUsuarios(
        construtor: WebClient.Builder,
        propriedades: UsuariosProperties,
    ): WebClient {
        val cliente = HttpClient.create()
            .option(
                ChannelOption.CONNECT_TIMEOUT_MILLIS,
                propriedades.timeoutDeConexao.toMillis().toInt(),
            )
            .responseTimeout(propriedades.timeoutDeResposta)
            .doOnConnected { conexao ->
                conexao.addHandlerLast(
                    ReadTimeoutHandler(propriedades.timeoutDeResposta.toMillis(), TimeUnit.MILLISECONDS),
                )
            }

        return construtor
            .baseUrl(propriedades.baseUrl)
            .clientConnector(ReactorClientHttpConnector(cliente))
            .build()
    }
}
