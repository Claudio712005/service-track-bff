package com.clau.service_track.bff.infra.client.catalogo

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface CatalogoApiClient {

    fun listarServicos(): Flux<ServicoResponse>

    fun listarInsumos(): Flux<InsumoResponse>

    fun buscarServico(id: String): Mono<ServicoResponse>

    fun buscarInsumo(id: String): Mono<InsumoResponse>

    fun saldoDe(insumoId: String): Mono<SaldoDeInsumoResponse>
}
