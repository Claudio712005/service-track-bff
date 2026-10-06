package com.clau.service_track.bff.infra.web.controller

import com.clau.service_track.bff.application.MecanicosService
import com.clau.service_track.bff.domain.Pessoa
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/mecanicos")
class MecanicoController(
    private val mecanicos: MecanicosService,
) {

    @GetMapping
    fun listar(): Flux<Pessoa> = mecanicos.listar()

    @GetMapping("/{id}")
    fun buscar(@PathVariable id: String): Mono<Pessoa> = mecanicos.buscar(id)
}
