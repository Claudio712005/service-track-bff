package com.clau.service_track.bff.infra.web.filter

import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.ExchangeFunction
import reactor.core.publisher.Mono
import reactor.util.context.ContextView

class OutboundHeadersFilter : ExchangeFilterFunction {

    override fun filter(request: ClientRequest, next: ExchangeFunction): Mono<ClientResponse> =
        Mono.deferContextual { context -> next.exchange(withHeaders(request, context)) }

    private fun withHeaders(request: ClientRequest, context: ContextView): ClientRequest {
        val builder = ClientRequest.from(request)

        forward(context, CorrelationFilter.CORRELATION_FIELD)?.let {
            builder.header(CorrelationFilter.CORRELATION_HEADER, it)
        }
        forward(context, CorrelationFilter.AUTHORIZATION_FIELD)?.let {
            builder.header(CorrelationFilter.AUTHORIZATION_HEADER, it)
        }

        return builder.build()
    }

    private fun forward(context: ContextView, field: String): String? =
        if (context.hasKey(field)) context.get<String>(field) else null
}
