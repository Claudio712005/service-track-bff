package com.clau.service_track.bff.infra.client.usuarios

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
@Profile("!dev")
class UsuariosWebClient(
    private val webClientDeUsuarios: WebClient,
) : UsuariosApiClient {

    override fun listarPorTipo(tipo: String): Flux<UsuarioResponse> = webClientDeUsuarios.get()
        .uri { construtor -> construtor.path(ROTA_DE_USUARIOS).queryParam("tipo", tipo).build() }
        .retrieve()
        .bodyToFlux(UsuarioResponse::class.java)

    override fun buscarPorId(id: String): Mono<UsuarioResponse> = webClientDeUsuarios.get()
        .uri("$ROTA_DE_USUARIOS/{id}", id)
        .retrieve()
        .bodyToMono(UsuarioResponse::class.java)

    override fun listarVeiculosDoCliente(clienteId: String): Flux<VeiculoResponse> = webClientDeUsuarios.get()
        .uri { construtor -> construtor.path(ROTA_DE_VEICULOS).queryParam("clienteId", clienteId).build() }
        .retrieve()
        .bodyToFlux(VeiculoResponse::class.java)

    private companion object {
        const val ROTA_DE_USUARIOS = "/usuarios"
        const val ROTA_DE_VEICULOS = "/veiculos"
    }
}
