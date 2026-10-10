package com.clau.service_track.bff.infra.web.filter

import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.ExchangeFunction
import reactor.core.publisher.Mono
import reactor.util.context.ContextView

class CabecalhosDeSaidaFilter : ExchangeFilterFunction {

    override fun filter(request: ClientRequest, next: ExchangeFunction): Mono<ClientResponse> =
        Mono.deferContextual { context -> next.exchange(comCabecalhos(request, context)) }

    private fun comCabecalhos(request: ClientRequest, context: ContextView): ClientRequest {
        val construtor = ClientRequest.from(request)

        repassar(context, CorrelationFilter.CORRELATION_FIELD)?.let {
            construtor.header(CorrelationFilter.CORRELATION_HEADER, it)
        }
        repassar(context, CorrelationFilter.AUTHORIZATION_FIELD)?.let {
            construtor.header(CorrelationFilter.AUTHORIZATION_HEADER, it)
        }

        return construtor.build()
    }

    private fun repassar(context: ContextView, campo: String): String? =
        if (context.hasKey(campo)) context.get<String>(campo) else null
}
