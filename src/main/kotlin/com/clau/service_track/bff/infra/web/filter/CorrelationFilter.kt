package com.clau.service_track.bff.infra.web.filter

import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import org.springframework.web.util.pattern.PathPattern
import reactor.core.publisher.Mono

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class CorrelationFilter : WebFilter {

    private val log = LoggerFactory.getLogger(CorrelationFilter::class.java)

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val correlation = sanitize(exchange.request.headers.getFirst(CORRELATION_HEADER)) ?: generate()
        val requestId = generate()

        exchange.response.headers.set(CORRELATION_HEADER, correlation)
        exchange.response.headers.set(REQUEST_HEADER, requestId)

        val start = System.nanoTime()

        return chain.filter(exchange)
            .doFinally {
                if (!skipped(exchange.request.path.value())) {
                    record(exchange, (System.nanoTime() - start) / 1_000_000)
                }
            }
            .contextWrite { context ->
                context.put(CORRELATION_FIELD, correlation).put(REQUEST_FIELD, requestId)
            }
    }

    private fun record(exchange: ServerWebExchange, durationMs: Long) {
        val method = exchange.request.method.name()
        val route = routeOf(exchange)
        val status = exchange.response.statusCode?.value() ?: 0

        when {
            status >= 500 -> log.error(FORMAT, method, route, status, durationMs)
            status >= 400 -> log.warn(FORMAT, method, route, status, durationMs)
            else -> log.info(FORMAT, method, route, status, durationMs)
        }
    }

    private fun skipped(path: String) = SKIPPED_PATHS.any { path.startsWith(it) }

    private fun sanitize(raw: String?): String? = raw
        ?.trim()
        ?.take(MAX_LENGTH)
        ?.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        ?.ifBlank { null }

    private fun generate(): String = UUID.randomUUID().toString()

    companion object {

        fun routeOf(exchange: ServerWebExchange): String =
            exchange.getAttribute<PathPattern>(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)
                ?.patternString
                ?: exchange.request.path.value()

        const val CORRELATION_HEADER = "X-Correlation-Id"
        const val REQUEST_HEADER = "X-Request-Id"
        const val CORRELATION_FIELD = "correlationId"
        const val REQUEST_FIELD = "requestId"
        private const val MAX_LENGTH = 64
        private const val FORMAT = "requisicao concluida metodo={} rota={} status={} duracaoMs={}"
        private val SKIPPED_PATHS = listOf("/actuator", "/v3/api-docs", "/swagger-ui", "/webjars")
    }
}
