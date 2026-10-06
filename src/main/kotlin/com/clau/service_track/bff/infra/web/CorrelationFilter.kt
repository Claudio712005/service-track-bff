package com.clau.service_track.bff.infra.web

import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class CorrelationFilter : WebFilter {

    private val log = LoggerFactory.getLogger(CorrelationFilter::class.java)

    override fun filter(troca: ServerWebExchange, cadeia: WebFilterChain): Mono<Void> {
        val correlacao = sanear(troca.request.headers.getFirst(CABECALHO_CORRELACAO)) ?: gerar()
        val requisicaoId = gerar()

        troca.response.headers.set(CABECALHO_CORRELACAO, correlacao)
        troca.response.headers.set(CABECALHO_REQUISICAO, requisicaoId)

        val inicio = System.nanoTime()

        return cadeia.filter(troca)
            .doFinally {
                if (!ignorado(troca.request.path.value())) {
                    registrar(troca, (System.nanoTime() - inicio) / 1_000_000)
                }
            }
            .contextWrite { contexto ->
                contexto.put(CHAVE_CORRELACAO, correlacao).put(CHAVE_REQUISICAO, requisicaoId)
            }
    }

    private fun registrar(troca: ServerWebExchange, duracaoMs: Long) {
        val metodo = troca.request.method.name()
        val rota = rotaDe(troca)
        val status = troca.response.statusCode?.value() ?: 0

        when {
            status >= 500 -> log.error(FORMATO, metodo, rota, status, duracaoMs)
            status >= 400 -> log.warn(FORMATO, metodo, rota, status, duracaoMs)
            else -> log.info(FORMATO, metodo, rota, status, duracaoMs)
        }
    }

    private fun ignorado(rota: String) = CAMINHOS_SEM_LOG.any { rota.startsWith(it) }

    private fun sanear(bruto: String?): String? = bruto
        ?.trim()
        ?.take(TAMANHO_MAXIMO)
        ?.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        ?.ifBlank { null }

    private fun gerar(): String = UUID.randomUUID().toString()

    companion object {

        fun rotaDe(troca: ServerWebExchange): String =
            troca.getAttribute<org.springframework.web.util.pattern.PathPattern>(
                org.springframework.web.reactive.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
            )?.patternString ?: troca.request.path.value()

        const val CABECALHO_CORRELACAO = "X-Correlation-Id"
        const val CABECALHO_REQUISICAO = "X-Request-Id"
        const val CHAVE_CORRELACAO = "correlationId"
        const val CHAVE_REQUISICAO = "requestId"
        private const val TAMANHO_MAXIMO = 64
        private const val FORMATO = "requisicao concluida metodo={} rota={} status={} duracaoMs={}"
        private val CAMINHOS_SEM_LOG = listOf("/actuator", "/v3/api-docs", "/swagger-ui", "/webjars")
    }
}
