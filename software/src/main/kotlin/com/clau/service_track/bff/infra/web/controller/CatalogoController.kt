package com.clau.service_track.bff.infra.web.controller

import com.clau.service_track.bff.application.port.`in`.CatalogoApiPort
import com.clau.service_track.bff.application.port.out.CatalogoPort
import com.clau.service_track.bff.domain.model.Insumo
import com.clau.service_track.bff.domain.model.SaldoDeInsumo
import com.clau.service_track.bff.domain.model.Servico
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
class CatalogoController(
    private val catalogo: CatalogoPort,
) : CatalogoApiPort {

    override fun servicos(): Flux<Servico> = catalogo.listarServicos()

    override fun insumos(): Flux<Insumo> = catalogo.listarInsumos()

    override fun saldo(id: String): Mono<SaldoDeInsumo> = catalogo.saldoDe(id)
}
