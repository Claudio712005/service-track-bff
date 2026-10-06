package com.clau.service_track.bff.domain.port

import com.clau.service_track.bff.domain.Pessoa
import com.clau.service_track.bff.domain.TipoDeUsuario
import com.clau.service_track.bff.domain.Veiculo
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface UsuariosPort {

    fun listarPorTipo(tipo: TipoDeUsuario): Flux<Pessoa>

    fun buscarPorId(id: String): Mono<Pessoa>

    fun listarVeiculosDoCliente(clienteId: String): Flux<Veiculo>
}
