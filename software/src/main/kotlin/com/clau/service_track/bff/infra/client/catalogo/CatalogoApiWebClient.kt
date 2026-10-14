package com.clau.service_track.bff.infra.client.catalogo

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
@Profile("!dev")
class CatalogoApiWebClient(
    private val catalogoWebClient: WebClient,
) : CatalogoApiClient {

    override fun listarServicos(): Flux<ServicoResponse> = catalogoWebClient.get()
        .uri(SERVICOS_ROUTE)
        .header(CABECALHO_DE_VERSAO, VERSAO)
        .retrieve()
        .bodyToFlux(ServicoResponse::class.java)

    override fun listarInsumos(): Flux<InsumoResponse> = catalogoWebClient.get()
        .uri(INSUMOS_ROUTE)
        .header(CABECALHO_DE_VERSAO, VERSAO)
        .retrieve()
        .bodyToFlux(InsumoResponse::class.java)

    override fun buscarServico(id: String): Mono<ServicoResponse> = catalogoWebClient.get()
        .uri("$SERVICOS_ROUTE/{id}", id)
        .header(CABECALHO_DE_VERSAO, VERSAO)
        .retrieve()
        .bodyToMono(ServicoResponse::class.java)

    override fun buscarInsumo(id: String): Mono<InsumoResponse> = catalogoWebClient.get()
        .uri("$INSUMOS_ROUTE/{id}", id)
        .header(CABECALHO_DE_VERSAO, VERSAO)
        .retrieve()
        .bodyToMono(InsumoResponse::class.java)

    override fun saldoDe(insumoId: String): Mono<SaldoDeInsumoResponse> = catalogoWebClient.get()
        .uri("$INSUMOS_ROUTE/{id}/estoque", insumoId)
        .header(CABECALHO_DE_VERSAO, VERSAO)
        .retrieve()
        .bodyToMono(SaldoDeInsumoResponse::class.java)

    private companion object {
        const val SERVICOS_ROUTE = "/servicos"
        const val INSUMOS_ROUTE = "/insumos"
        const val CABECALHO_DE_VERSAO = "X-API-Version"
        const val VERSAO = "1"
    }
}
