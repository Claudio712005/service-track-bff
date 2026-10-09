package com.clau.service_track.bff.application.port.out

import com.clau.service_track.bff.domain.model.Insumo
import com.clau.service_track.bff.domain.model.SaldoDeInsumo
import com.clau.service_track.bff.domain.model.Servico
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface CatalogoPort {

    fun listarServicos(): Flux<Servico>

    fun listarInsumos(): Flux<Insumo>

    fun buscarServicos(ids: Set<String>): Mono<Map<String, Servico>>

    fun buscarInsumos(ids: Set<String>): Mono<Map<String, Insumo>>

    fun saldoDe(insumoId: String): Mono<SaldoDeInsumo>
}
