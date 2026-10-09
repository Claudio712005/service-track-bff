package com.clau.service_track.bff.infra.client.catalogo

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
@Profile("!dev")
class CatalogoWebClient(
    private val webClientDeCatalogo: WebClient,
) : CatalogoApiClient {

    override fun listarServicos(): Flux<ServicoResponse> = webClientDeCatalogo.get()
        .uri(ROTA_DE_SERVICOS)
        .header(CABECALHO_DE_VERSAO, VERSAO)
        .retrieve()
        .bodyToFlux(ServicoResponse::class.java)

    override fun listarInsumos(): Flux<InsumoResponse> = webClientDeCatalogo.get()
        .uri(ROTA_DE_INSUMOS)
        .header(CABECALHO_DE_VERSAO, VERSAO)
        .retrieve()
        .bodyToFlux(InsumoResponse::class.java)

    override fun buscarServico(id: String): Mono<ServicoResponse> = webClientDeCatalogo.get()
        .uri("$ROTA_DE_SERVICOS/{id}", id)
        .header(CABECALHO_DE_VERSAO, VERSAO)
        .retrieve()
        .bodyToMono(ServicoResponse::class.java)

    override fun buscarInsumo(id: String): Mono<InsumoResponse> = webClientDeCatalogo.get()
        .uri("$ROTA_DE_INSUMOS/{id}", id)
        .header(CABECALHO_DE_VERSAO, VERSAO)
        .retrieve()
        .bodyToMono(InsumoResponse::class.java)

    override fun saldoDe(insumoId: String): Mono<SaldoDeInsumoResponse> = webClientDeCatalogo.get()
        .uri("$ROTA_DE_INSUMOS/{id}/estoque", insumoId)
        .header(CABECALHO_DE_VERSAO, VERSAO)
        .retrieve()
        .bodyToMono(SaldoDeInsumoResponse::class.java)

    private companion object {
        const val ROTA_DE_SERVICOS = "/servicos"
        const val ROTA_DE_INSUMOS = "/insumos"
        const val CABECALHO_DE_VERSAO = "X-API-Version"
        const val VERSAO = "1"
    }
}
