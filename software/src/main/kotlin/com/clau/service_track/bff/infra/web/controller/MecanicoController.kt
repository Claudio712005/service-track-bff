package com.clau.service_track.bff.infra.web.controller

import com.clau.service_track.bff.application.port.`in`.MecanicoApiPort
import com.clau.service_track.bff.application.service.MecanicosService
import com.clau.service_track.bff.domain.model.Pessoa
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
class MecanicoController(
    private val mecanicos: MecanicosService,
) : MecanicoApiPort {

    override fun listar(): Flux<Pessoa> = mecanicos.listar()

    override fun buscar(id: String): Mono<Pessoa> = mecanicos.buscar(id)
}
