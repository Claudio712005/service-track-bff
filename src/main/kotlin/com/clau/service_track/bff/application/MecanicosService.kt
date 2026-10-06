package com.clau.service_track.bff.application

import com.clau.service_track.bff.domain.Pessoa
import com.clau.service_track.bff.domain.TipoDeUsuario
import com.clau.service_track.bff.domain.exception.RecursoNaoEncontradoException
import com.clau.service_track.bff.domain.port.UsuariosPort
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Service
class MecanicosService(
    private val usuarios: UsuariosPort,
) {

    fun listar(): Flux<Pessoa> = usuarios.listarPorTipo(TipoDeUsuario.MECANICO)

    fun buscar(id: String): Mono<Pessoa> = usuarios.buscarPorId(id)
        .switchIfEmpty(Mono.error { RecursoNaoEncontradoException("Mecanico $id nao encontrado") })
        .flatMap { pessoa ->
            if (pessoa.tipoDeUsuario == TipoDeUsuario.MECANICO) {
                Mono.just(pessoa)
            } else {
                Mono.error(RecursoNaoEncontradoException("Mecanico $id nao encontrado"))
            }
        }
}
