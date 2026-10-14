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

class CallGuard(
    private val service: String,
    private val retry: Retry,
    private val circuitBreaker: CircuitBreaker,
) {

    private val log = LoggerFactory.getLogger(CallGuard::class.java)

    fun <T : Any> mono(route: String, call: () -> Mono<T>): Mono<T> = Mono.defer(call)
        .onErrorMap { error -> translate(route, error) }
        .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
        .transformDeferred(RetryOperator.of(retry))

    fun <T : Any> flux(route: String, call: () -> Flux<T>): Flux<T> = Flux.defer(call)
        .onErrorMap { error -> translate(route, error) }
        .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
        .transformDeferred(RetryOperator.of(retry))

    fun <T : Any> fluxOrEmpty(route: String, call: () -> Flux<T>): Flux<T> = flux(route, call)
        .onErrorResume(RecursoNaoEncontradoException::class.java) { Flux.empty() }

    private fun translate(route: String, error: Throwable): Throwable = when {
        error is WebClientResponseException && error.statusCode.value() == NOT_FOUND ->
            RecursoNaoEncontradoException("Recurso nao encontrado em $route")

        error is WebClientResponseException && error.statusCode.value() in CREDENTIAL_REJECTED -> {
            log.warn("servico {} recusou a credencial rota={} status={}", service, route, error.statusCode.value())
            NaoAutorizadoException("Credencial recusada por $service")
        }

        error is WebClientResponseException && error.statusCode.is4xxClientError -> {
            log.warn("servico {} recusou a requisicao rota={} status={}", service, route, error.statusCode.value())
            error
        }

        error is WebClientResponseException -> {
            log.warn("servico {} respondeu erro rota={} status={}", service, route, error.statusCode.value())
            ServicoIndisponivelException(service, error)
        }

        error is WebClientRequestException -> {
            log.warn("falha de rede ao chamar {} rota={} causa={}", service, route, error.message)
            ServicoIndisponivelException(service, error)
        }

        else -> error
    }

    private companion object {
        const val NOT_FOUND = 404
        val CREDENTIAL_REJECTED = listOf(401, 403)
    }
}
