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
    private val cliente: UsuariosApiClient,
    usuariosRetry: Retry,
    usuariosCircuitBreaker: CircuitBreaker,
) : UsuariosPort {

    private val guard = CallGuard("usuarios", usuariosRetry, usuariosCircuitBreaker)

    override fun listarPorTipo(tipo: TipoDeUsuario): Flux<Pessoa> =
        guard.fluxOrEmpty(ROTA_DE_USUARIOS) { cliente.listarPorTipo(tipo.name) }.map(::paraPessoa)

    override fun buscarPorId(id: String): Mono<Pessoa> =
        guard.mono("$ROTA_DE_USUARIOS/{id}") { cliente.buscarPorId(id) }.map(::paraPessoa)

    override fun listarVeiculosDoCliente(clienteId: String): Flux<Veiculo> =
        guard.fluxOrEmpty(ROTA_DE_VEICULOS) { cliente.listarVeiculosDoCliente(clienteId) }.map(::paraVeiculo)

    private fun paraPessoa(resposta: UsuarioResponse) = Pessoa(
        id = resposta.id,
        documento = resposta.documento,
        nome = resposta.nome,
        email = resposta.email,
        tipoDeUsuario = TipoDeUsuario.valueOf(resposta.tipoDeUsuario),
    )

    private fun paraVeiculo(resposta: VeiculoResponse) = Veiculo(
        id = resposta.id,
        placa = resposta.placa,
        marca = resposta.marca,
        modelo = resposta.modelo,
        anoModelo = resposta.anoModelo,
    )

    private companion object {
        const val ROTA_DE_USUARIOS = "/usuarios"
        const val ROTA_DE_VEICULOS = "/veiculos"
    }
}
