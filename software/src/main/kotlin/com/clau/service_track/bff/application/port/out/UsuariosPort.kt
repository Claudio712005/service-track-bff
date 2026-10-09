package com.clau.service_track.bff.application.port.out

import com.clau.service_track.bff.domain.model.Pessoa
import com.clau.service_track.bff.domain.model.TipoDeUsuario
import com.clau.service_track.bff.domain.model.Veiculo
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface UsuariosPort {

    fun listarPorTipo(tipo: TipoDeUsuario): Flux<Pessoa>

    fun buscarPorId(id: String): Mono<Pessoa>

    fun listarVeiculosDoCliente(clienteId: String): Flux<Veiculo>
}
