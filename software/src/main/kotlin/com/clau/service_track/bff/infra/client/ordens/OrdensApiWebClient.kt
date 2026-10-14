package com.clau.service_track.bff.infra.client.ordens

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
@Profile("!dev")
class OrdensApiWebClient(
    private val ordensWebClient: WebClient,
) : OrdensApiClient {

    override fun abrir(requisicao: AbrirOrdemRequest): Mono<OrdemServicoResponse> = ordensWebClient.post()
        .uri(ROUTE)
        .bodyValue(requisicao)
        .retrieve()
        .bodyToMono(OrdemServicoResponse::class.java)

    override fun buscarPorId(id: String): Mono<OrdemServicoResponse> = ordensWebClient.get()
        .uri("$ROUTE/{id}", id)
        .retrieve()
        .bodyToMono(OrdemServicoResponse::class.java)

    override fun listarDoCliente(clienteId: String, tamanho: Int): Mono<PaginaDeOrdensResponse> =
        ordensWebClient.get()
            .uri { construtor ->
                construtor.path(ROUTE)
                    .queryParam("clienteId", clienteId)
                    .queryParam("tamanho", tamanho)
                    .build()
            }
            .retrieve()
            .bodyToMono(PaginaDeOrdensResponse::class.java)

    override fun historicoDe(id: String): Flux<FatoDaOrdemResponse> = ordensWebClient.get()
        .uri("$ROUTE/{id}/historico", id)
        .retrieve()
        .bodyToFlux(FatoDaOrdemResponse::class.java)

    override fun sagasDe(id: String): Flux<SagaResponse> = ordensWebClient.get()
        .uri("$ROUTE/{id}/saga", id)
        .retrieve()
        .bodyToFlux(SagaResponse::class.java)

    override fun aprovarOrcamento(id: String): Mono<OrdemServicoResponse> = ordensWebClient.post()
        .uri("$ROUTE/{id}/orcamento/aprovacao", id)
        .retrieve()
        .bodyToMono(OrdemServicoResponse::class.java)

    private companion object {
        const val ROUTE = "/ordens"
    }
}
