package com.clau.service_track.bff.infra.client.ordens

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface OrdensApiClient {

    fun abrir(requisicao: AbrirOrdemRequest): Mono<OrdemServicoResponse>

    fun buscarPorId(id: String): Mono<OrdemServicoResponse>

    fun listarDoCliente(clienteId: String, tamanho: Int): Mono<PaginaDeOrdensResponse>

    fun historicoDe(id: String): Flux<FatoDaOrdemResponse>

    fun sagasDe(id: String): Flux<SagaResponse>

    fun aprovarOrcamento(id: String): Mono<OrdemServicoResponse>
}
