package com.clau.service_track.bff.infra.web

import com.clau.service_track.bff.domain.exception.NaoAutorizadoException
import com.clau.service_track.bff.domain.exception.RecursoNaoEncontradoException
import com.clau.service_track.bff.domain.exception.RequisicaoInvalidaException
import com.clau.service_track.bff.domain.exception.ServicoIndisponivelException
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import reactor.util.context.ContextView

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(RecursoNaoEncontradoException::class)
    fun naoEncontrado(erro: RecursoNaoEncontradoException, troca: ServerWebExchange) =
        montar(HttpStatus.NOT_FOUND, erro.message, troca)

    @ExceptionHandler(RequisicaoInvalidaException::class)
    fun requisicaoInvalida(erro: RequisicaoInvalidaException, troca: ServerWebExchange) =
        montar(HttpStatus.BAD_REQUEST, erro.message, troca)

    @ExceptionHandler(NaoAutorizadoException::class)
    fun naoAutorizado(erro: NaoAutorizadoException, troca: ServerWebExchange) =
        montar(HttpStatus.UNAUTHORIZED, erro.message, troca)

    @ExceptionHandler(ServicoIndisponivelException::class)
    fun servicoIndisponivel(erro: ServicoIndisponivelException, troca: ServerWebExchange): Mono<ResponseEntity<ErrorResponse>> {
        log.error("dependencia indisponivel servico={} rota={}", erro.servico, troca.request.path.value())
        return montar(HttpStatus.SERVICE_UNAVAILABLE, erro.message, troca)
    }

    @ExceptionHandler(CallNotPermittedException::class)
    fun disjuntorAberto(erro: CallNotPermittedException, troca: ServerWebExchange): Mono<ResponseEntity<ErrorResponse>> {
        log.error("disjuntor aberto, chamada recusada sem tentar disjuntor={}", erro.causingCircuitBreakerName)
        return montar(HttpStatus.SERVICE_UNAVAILABLE, "Dependencia indisponivel, tente novamente em instantes", troca)
    }

    @ExceptionHandler(Exception::class)
    fun naoPrevisto(erro: Exception, troca: ServerWebExchange): Mono<ResponseEntity<ErrorResponse>> {
        log.error("falha nao prevista rota={}", troca.request.path.value(), erro)
        return montar(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno", troca)
    }

    private fun montar(
        status: HttpStatus,
        mensagem: String?,
        troca: ServerWebExchange,
    ): Mono<ResponseEntity<ErrorResponse>> = Mono.deferContextual { contexto ->
        Mono.just(
            ResponseEntity.status(status).body(
                ErrorResponse(
                    status = status.value(),
                    erro = status.reasonPhrase,
                    mensagem = mensagem ?: status.reasonPhrase,
                    rota = troca.request.path.value(),
                    correlationId = doContexto(contexto, CorrelationFilter.CORRELATION_FIELD),
                    requestId = doContexto(contexto, CorrelationFilter.REQUEST_FIELD),
                ),
            ),
        )
    }

    private fun doContexto(contexto: ContextView, campo: String): String? =
        if (contexto.hasKey(campo)) contexto.get(campo) else null
}
