package com.clau.service_track.bff.infra.usuarios

import com.clau.service_track.bff.infra.web.CorrelationFilter
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.ExchangeFunction
import reactor.core.publisher.Mono

class CorrelationPropagationFilter : ExchangeFilterFunction {

    override fun filter(request: ClientRequest, next: ExchangeFunction): Mono<ClientResponse> =
        Mono.deferContextual { context ->
            if (!context.hasKey(CorrelationFilter.CORRELATION_FIELD)) {
                return@deferContextual next.exchange(request)
            }

            val comCorrelacao = ClientRequest.from(request)
                .header(
                    CorrelationFilter.CORRELATION_HEADER,
                    context.get<String>(CorrelationFilter.CORRELATION_FIELD),
                )
                .build()

            next.exchange(comCorrelacao)
        }
}
