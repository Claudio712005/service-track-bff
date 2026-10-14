package com.clau.service_track.bff.infra.client.usuarios

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
@Profile("!dev")
class UsuariosApiWebClient(
    private val usuariosWebClient: WebClient,
) : UsuariosApiClient {

    override fun listarPorTipo(tipo: String): Flux<UsuarioResponse> = usuariosWebClient.get()
        .uri { construtor -> construtor.path(USUARIOS_ROUTE).queryParam("tipo", tipo).build() }
        .retrieve()
        .bodyToFlux(UsuarioResponse::class.java)

    override fun buscarPorId(id: String): Mono<UsuarioResponse> = usuariosWebClient.get()
        .uri("$USUARIOS_ROUTE/{id}", id)
        .retrieve()
        .bodyToMono(UsuarioResponse::class.java)

    override fun listarVeiculosDoCliente(clienteId: String): Flux<VeiculoResponse> = usuariosWebClient.get()
        .uri { construtor -> construtor.path(VEICULOS_ROUTE).queryParam("clienteId", clienteId).build() }
        .retrieve()
        .bodyToFlux(VeiculoResponse::class.java)

    private companion object {
        const val USUARIOS_ROUTE = "/usuarios"
        const val VEICULOS_ROUTE = "/veiculos"
    }
}
