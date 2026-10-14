package com.clau.service_track.bff.infra.web.error

import com.clau.service_track.bff.domain.exception.NaoAutorizadoException
import com.clau.service_track.bff.domain.exception.RecursoNaoEncontradoException
import com.clau.service_track.bff.domain.exception.RequisicaoInvalidaException
import com.clau.service_track.bff.domain.exception.ServicoIndisponivelException
import com.clau.service_track.bff.infra.web.filter.CorrelationFilter
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.server.ServerWebInputException
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import reactor.util.context.ContextView

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(RecursoNaoEncontradoException::class)
    fun notFound(error: RecursoNaoEncontradoException, exchange: ServerWebExchange) =
        build(HttpStatus.NOT_FOUND, error.message, exchange)

    @ExceptionHandler(RequisicaoInvalidaException::class)
    fun invalidRequest(error: RequisicaoInvalidaException, exchange: ServerWebExchange) =
        build(HttpStatus.BAD_REQUEST, error.message, exchange)

    @ExceptionHandler(WebExchangeBindException::class)
    fun invalidBody(error: WebExchangeBindException, exchange: ServerWebExchange): Mono<ResponseEntity<ErrorResponse>> {
        val violations = error.fieldErrors.map { "${it.field}: ${it.defaultMessage.orEmpty()}" }
        return build(
            HttpStatus.BAD_REQUEST,
            "Corpo da requisicao invalido: ${violations.joinToString("; ")}",
            exchange,
        )
    }

    @ExceptionHandler(ServerWebInputException::class)
    fun invalidInput(error: ServerWebInputException, exchange: ServerWebExchange) =
        build(HttpStatus.BAD_REQUEST, error.reason ?: "Entrada da requisicao invalida", exchange)

    @ExceptionHandler(ResponseStatusException::class)
    fun frameworkStatus(error: ResponseStatusException, exchange: ServerWebExchange) = build(
        HttpStatus.valueOf(error.statusCode.value()),
        error.reason ?: "Requisicao recusada",
        exchange,
    )

    @ExceptionHandler(NaoAutorizadoException::class)
    fun unauthorized(error: NaoAutorizadoException, exchange: ServerWebExchange) =
        build(HttpStatus.UNAUTHORIZED, error.message, exchange)

    @ExceptionHandler(ServicoIndisponivelException::class)
    fun serviceUnavailable(error: ServicoIndisponivelException, exchange: ServerWebExchange): Mono<ResponseEntity<ErrorResponse>> {
        log.error("dependencia indisponivel servico={} rota={}", error.servico, exchange.request.path.value())
        return build(HttpStatus.SERVICE_UNAVAILABLE, error.message, exchange)
    }

    @ExceptionHandler(CallNotPermittedException::class)
    fun circuitBreakerOpen(error: CallNotPermittedException, exchange: ServerWebExchange): Mono<ResponseEntity<ErrorResponse>> {
        log.error("disjuntor aberto, chamada recusada sem tentar disjuntor={}", error.causingCircuitBreakerName)
        return build(HttpStatus.SERVICE_UNAVAILABLE, "Dependencia indisponivel, tente novamente em instantes", exchange)
    }

    @ExceptionHandler(Exception::class)
    fun unexpected(error: Exception, exchange: ServerWebExchange): Mono<ResponseEntity<ErrorResponse>> {
        log.error("falha nao prevista rota={}", exchange.request.path.value(), error)
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno", exchange)
    }

    private fun build(
        status: HttpStatus,
        message: String?,
        exchange: ServerWebExchange,
    ): Mono<ResponseEntity<ErrorResponse>> = Mono.deferContextual { context ->
        Mono.just(
            ResponseEntity.status(status).body(
                ErrorResponse(
                    status = status.value(),
                    erro = status.reasonPhrase,
                    mensagem = message ?: status.reasonPhrase,
                    rota = exchange.request.path.value(),
                    correlationId = fromContext(context, CorrelationFilter.CORRELATION_FIELD),
                    requestId = fromContext(context, CorrelationFilter.REQUEST_FIELD),
                ),
            ),
        )
    }

    private fun fromContext(context: ContextView, field: String): String? =
        if (context.hasKey(field)) context.get(field) else null
}
