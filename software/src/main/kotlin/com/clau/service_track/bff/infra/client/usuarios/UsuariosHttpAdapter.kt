package com.clau.service_track.bff.infra.client.usuarios

import com.clau.service_track.bff.application.port.out.UsuariosPort
import com.clau.service_track.bff.domain.model.Pessoa
import com.clau.service_track.bff.domain.model.TipoDeUsuario
import com.clau.service_track.bff.domain.model.Veiculo
import com.clau.service_track.bff.infra.client.CallGuard
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.retry.Retry
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
@Profile("!dev")
class UsuariosHttpAdapter(
    private val apiClient: UsuariosApiClient,
    usuariosRetry: Retry,
    usuariosCircuitBreaker: CircuitBreaker,
) : UsuariosPort {

    private val guard = CallGuard("usuarios", usuariosRetry, usuariosCircuitBreaker)

    override fun listarPorTipo(tipo: TipoDeUsuario): Flux<Pessoa> =
        guard.fluxOrEmpty(USUARIOS_ROUTE) { apiClient.listarPorTipo(tipo.name) }.map(::toPessoa)

    override fun buscarPorId(id: String): Mono<Pessoa> =
        guard.mono("$USUARIOS_ROUTE/{id}") { apiClient.buscarPorId(id) }.map(::toPessoa)

    override fun listarVeiculosDoCliente(clienteId: String): Flux<Veiculo> =
        guard.fluxOrEmpty(VEICULOS_ROUTE) { apiClient.listarVeiculosDoCliente(clienteId) }.map(::toVeiculo)

    private fun toPessoa(resposta: UsuarioResponse) = Pessoa(
        id = resposta.id,
        documento = resposta.documento,
        nome = resposta.nome,
        email = resposta.email,
        tipoDeUsuario = TipoDeUsuario.valueOf(resposta.tipoDeUsuario),
    )

    private fun toVeiculo(resposta: VeiculoResponse) = Veiculo(
        id = resposta.id,
        placa = resposta.placa,
        marca = resposta.marca,
        modelo = resposta.modelo,
        anoModelo = resposta.anoModelo,
    )

    private companion object {
        const val USUARIOS_ROUTE = "/usuarios"
        const val VEICULOS_ROUTE = "/veiculos"
    }
}
