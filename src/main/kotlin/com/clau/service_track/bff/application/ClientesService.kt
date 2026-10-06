package com.clau.service_track.bff.application

import com.clau.service_track.bff.domain.ClienteVeiculos
import com.clau.service_track.bff.domain.Pessoa
import com.clau.service_track.bff.domain.TipoDeUsuario
import com.clau.service_track.bff.domain.exception.RecursoNaoEncontradoException
import com.clau.service_track.bff.domain.exception.ServicoIndisponivelException
import com.clau.service_track.bff.domain.port.UsuariosPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Service
class ClientesService(
    private val usuarios: UsuariosPort,
) {

    private val log = LoggerFactory.getLogger(ClientesService::class.java)

    fun listar(): Flux<Pessoa> = usuarios.listarPorTipo(TipoDeUsuario.CLIENTE)

    fun comVeiculos(clienteId: String): Mono<ClienteVeiculos> = usuarios.buscarPorId(clienteId)
        .switchIfEmpty(Mono.error { RecursoNaoEncontradoException("Cliente $clienteId nao encontrado") })
        .flatMap { cliente -> exigirCliente(cliente, clienteId) }
        .flatMap { cliente -> comVeiculosDe(cliente, clienteId) }

    private fun exigirCliente(cliente: Pessoa, clienteId: String): Mono<Pessoa> =
        if (cliente.tipoDeUsuario == TipoDeUsuario.CLIENTE) {
            Mono.just(cliente)
        } else {
            Mono.error(RecursoNaoEncontradoException("Cliente $clienteId nao encontrado"))
        }

    private fun comVeiculosDe(cliente: Pessoa, clienteId: String): Mono<ClienteVeiculos> =
        usuarios.listarVeiculosDoCliente(clienteId)
            .collectList()
            .map { veiculos ->
                ClienteVeiculos(cliente = cliente, veiculos = veiculos, veiculosIndisponiveis = false)
            }
            .onErrorResume(ServicoIndisponivelException::class.java) { erro ->
                log.warn("cliente devolvido sem veiculos clienteId={} motivo={}", clienteId, erro.message)
                Mono.just(
                    ClienteVeiculos(cliente = cliente, veiculos = emptyList(), veiculosIndisponiveis = true),
                )
            }
}
