package com.clau.service_track.bff.infra.client

import com.clau.service_track.bff.domain.exception.NaoAutorizadoException
import com.clau.service_track.bff.domain.exception.RecursoNaoEncontradoException
import com.clau.service_track.bff.domain.exception.ServicoIndisponivelException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator
import io.github.resilience4j.reactor.retry.RetryOperator
import io.github.resilience4j.retry.Retry
import org.slf4j.LoggerFactory
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class ProtecaoDeChamada(
    private val servico: String,
    private val retry: Retry,
    private val disjuntor: CircuitBreaker,
) {

    private val log = LoggerFactory.getLogger(ProtecaoDeChamada::class.java)

    fun <T : Any> mono(rota: String, chamada: () -> Mono<T>): Mono<T> = Mono.defer(chamada)
        .onErrorMap { erro -> traduzir(rota, erro) }
        .transformDeferred(CircuitBreakerOperator.of(disjuntor))
        .transformDeferred(RetryOperator.of(retry))

    fun <T : Any> flux(rota: String, chamada: () -> Flux<T>): Flux<T> = Flux.defer(chamada)
        .onErrorMap { erro -> traduzir(rota, erro) }
        .transformDeferred(CircuitBreakerOperator.of(disjuntor))
        .transformDeferred(RetryOperator.of(retry))

    fun <T : Any> fluxTolerante(rota: String, chamada: () -> Flux<T>): Flux<T> = flux(rota, chamada)
        .onErrorResume(RecursoNaoEncontradoException::class.java) { Flux.empty() }

    private fun traduzir(rota: String, erro: Throwable): Throwable = when {
        erro is WebClientResponseException && erro.statusCode.value() == NAO_ENCONTRADO ->
            RecursoNaoEncontradoException("Recurso nao encontrado em $rota")

        erro is WebClientResponseException && erro.statusCode.value() in CREDENCIAL_RECUSADA -> {
            log.warn("servico {} recusou a credencial rota={} status={}", servico, rota, erro.statusCode.value())
            NaoAutorizadoException("Credencial recusada por $servico")
        }

        erro is WebClientResponseException && erro.statusCode.is4xxClientError -> {
            log.warn("servico {} recusou a requisicao rota={} status={}", servico, rota, erro.statusCode.value())
            erro
        }

        erro is WebClientResponseException -> {
            log.warn("servico {} respondeu erro rota={} status={}", servico, rota, erro.statusCode.value())
            ServicoIndisponivelException(servico, erro)
        }

        erro is WebClientRequestException -> {
            log.warn("falha de rede ao chamar {} rota={} causa={}", servico, rota, erro.message)
            ServicoIndisponivelException(servico, erro)
        }

        else -> erro
    }

    private companion object {
        const val NAO_ENCONTRADO = 404
        val CREDENCIAL_RECUSADA = listOf(401, 403)
    }
}
