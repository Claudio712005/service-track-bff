package com.clau.service_track.bff.infra.client.ordens

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
@Profile("!dev")
class OrdensWebClient(
    private val webClientDeOrdens: WebClient,
) : OrdensApiClient {

    override fun abrir(requisicao: AbrirOrdemRequest): Mono<OrdemServicoResponse> = webClientDeOrdens.post()
        .uri(ROTA)
        .bodyValue(requisicao)
        .retrieve()
        .bodyToMono(OrdemServicoResponse::class.java)

    override fun buscarPorId(id: String): Mono<OrdemServicoResponse> = webClientDeOrdens.get()
        .uri("$ROTA/{id}", id)
        .retrieve()
        .bodyToMono(OrdemServicoResponse::class.java)

    override fun listarDoCliente(clienteId: String, tamanho: Int): Mono<PaginaDeOrdensResponse> =
        webClientDeOrdens.get()
            .uri { construtor ->
                construtor.path(ROTA)
                    .queryParam("clienteId", clienteId)
                    .queryParam("tamanho", tamanho)
                    .build()
            }
            .retrieve()
            .bodyToMono(PaginaDeOrdensResponse::class.java)

    override fun historicoDe(id: String): Flux<FatoDaOrdemResponse> = webClientDeOrdens.get()
        .uri("$ROTA/{id}/historico", id)
        .retrieve()
        .bodyToFlux(FatoDaOrdemResponse::class.java)

    override fun sagasDe(id: String): Flux<SagaResponse> = webClientDeOrdens.get()
        .uri("$ROTA/{id}/saga", id)
        .retrieve()
        .bodyToFlux(SagaResponse::class.java)

    override fun aprovarOrcamento(id: String): Mono<OrdemServicoResponse> = webClientDeOrdens.post()
        .uri("$ROTA/{id}/orcamento/aprovacao", id)
        .retrieve()
        .bodyToMono(OrdemServicoResponse::class.java)

    private companion object {
        const val ROTA = "/ordens"
    }
}
