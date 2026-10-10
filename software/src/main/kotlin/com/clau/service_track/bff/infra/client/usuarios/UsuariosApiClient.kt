package com.clau.service_track.bff.infra.client.usuarios

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface UsuariosApiClient {

    fun listarPorTipo(tipo: String): Flux<UsuarioResponse>

    fun buscarPorId(id: String): Mono<UsuarioResponse>

    fun listarVeiculosDoCliente(clienteId: String): Flux<VeiculoResponse>
}
