package com.clau.service_track.bff.infra.client.usuarios

import com.clau.service_track.bff.application.port.out.UsuariosPort
import com.clau.service_track.bff.domain.exception.RecursoNaoEncontradoException
import com.clau.service_track.bff.domain.exception.ServicoIndisponivelException
import com.clau.service_track.bff.domain.model.Pessoa
import com.clau.service_track.bff.domain.model.TipoDeUsuario
import com.clau.service_track.bff.domain.model.Veiculo
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator
import io.github.resilience4j.reactor.retry.RetryOperator
import io.github.resilience4j.retry.Retry
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
@Profile("!dev")
class UsuariosHttpAdapter(
    private val cliente: UsuariosApiClient,
    private val retryDeUsuarios: Retry,
    private val disjuntorDeUsuarios: CircuitBreaker,
) : UsuariosPort {

    private val log = LoggerFactory.getLogger(UsuariosHttpAdapter::class.java)

    override fun listarPorTipo(tipo: TipoDeUsuario): Flux<Pessoa> =
        protegerFluxo(ROTA_DE_USUARIOS) { cliente.listarPorTipo(tipo.name) }.map(::paraPessoa)

    override fun buscarPorId(id: String): Mono<Pessoa> =
        proteger("$ROTA_DE_USUARIOS/{id}") { cliente.buscarPorId(id) }.map(::paraPessoa)

    override fun listarVeiculosDoCliente(clienteId: String): Flux<Veiculo> =
        protegerFluxo(ROTA_DE_VEICULOS) { cliente.listarVeiculosDoCliente(clienteId) }.map(::paraVeiculo)

    private fun <T : Any> protegerFluxo(rota: String, chamada: () -> Flux<T>): Flux<T> =
        Flux.defer(chamada)
            .onErrorMap { erro -> traduzir(rota, erro) }
            .transformDeferred(CircuitBreakerOperator.of(disjuntorDeUsuarios))
            .transformDeferred(RetryOperator.of(retryDeUsuarios))
            .onErrorResume(RecursoNaoEncontradoException::class.java) { Flux.empty() }

    private fun <T : Any> proteger(rota: String, chamada: () -> Mono<T>): Mono<T> =
        Mono.defer(chamada)
            .onErrorMap { erro -> traduzir(rota, erro) }
            .transformDeferred(CircuitBreakerOperator.of(disjuntorDeUsuarios))
            .transformDeferred(RetryOperator.of(retryDeUsuarios))

    private fun traduzir(rota: String, erro: Throwable): Throwable = when {
        erro is WebClientResponseException && erro.statusCode.value() == NAO_ENCONTRADO ->
            RecursoNaoEncontradoException("Recurso nao encontrado em $rota")

        erro is WebClientResponseException -> {
            log.warn("servico de usuarios respondeu erro rota={} status={}", rota, erro.statusCode.value())
            ServicoIndisponivelException("usuarios", erro)
        }

        erro is WebClientRequestException -> {
            log.warn("falha de rede ao chamar usuarios rota={} causa={}", rota, erro.message)
            ServicoIndisponivelException("usuarios", erro)
        }

        else -> erro
    }

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
        const val NAO_ENCONTRADO = 404
    }
}
