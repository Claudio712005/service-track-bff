package com.clau.service_track.bff.infra.usuarios

import com.clau.service_track.bff.domain.Pessoa
import com.clau.service_track.bff.domain.TipoDeUsuario
import com.clau.service_track.bff.domain.Veiculo
import com.clau.service_track.bff.domain.exception.RecursoNaoEncontradoException
import com.clau.service_track.bff.domain.exception.ServicoIndisponivelException
import com.clau.service_track.bff.domain.port.UsuariosPort
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator
import io.github.resilience4j.reactor.retry.RetryOperator
import io.github.resilience4j.retry.Retry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
class UsuariosHttpAdapter(
    private val webClientDeUsuarios: WebClient,
    private val retryDeUsuarios: Retry,
    private val disjuntorDeUsuarios: CircuitBreaker,
) : UsuariosPort {

    private val log = LoggerFactory.getLogger(UsuariosHttpAdapter::class.java)

    override fun listarPorTipo(tipo: TipoDeUsuario): Flux<Pessoa> = executar(ROTA_DE_USUARIOS) {
        webClientDeUsuarios.get()
            .uri { construtor -> construtor.path(ROTA_DE_USUARIOS).queryParam("tipo", tipo.name).build() }
            .retrieve()
            .bodyToFlux(UsuarioResponse::class.java)
    }.map(::paraPessoa)

    override fun buscarPorId(id: String): Mono<Pessoa> = executarUnico("$ROTA_DE_USUARIOS/{id}") {
        webClientDeUsuarios.get()
            .uri("$ROTA_DE_USUARIOS/{id}", id)
            .retrieve()
            .bodyToMono(UsuarioResponse::class.java)
    }.map(::paraPessoa)

    override fun listarVeiculosDoCliente(clienteId: String): Flux<Veiculo> = executar(ROTA_DE_VEICULOS) {
        webClientDeUsuarios.get()
            .uri { construtor -> construtor.path(ROTA_DE_VEICULOS).queryParam("clienteId", clienteId).build() }
            .retrieve()
            .bodyToFlux(VeiculoResponse::class.java)
    }.map(::paraVeiculo)

    private fun <T : Any> executar(rota: String, chamada: () -> Flux<T>): Flux<T> =
        Flux.defer(chamada)
            .onErrorMap { erro -> traduzir(rota, erro) }
            .transformDeferred(CircuitBreakerOperator.of(disjuntorDeUsuarios))
            .transformDeferred(RetryOperator.of(retryDeUsuarios))
            .onErrorResume(RecursoNaoEncontradoException::class.java) { Flux.empty() }

    private fun <T : Any> executarUnico(rota: String, chamada: () -> Mono<T>): Mono<T> =
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
