package com.clau.service_track.bff.infra.web

import com.clau.service_track.bff.application.ClientesService
import com.clau.service_track.bff.domain.ClienteVeiculos
import com.clau.service_track.bff.domain.Pessoa
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/clientes")
class ClienteController(
    private val clientes: ClientesService,
) {

    @GetMapping
    fun listar(): Flux<Pessoa> = clientes.listar()

    @GetMapping("/{id}")
    fun buscar(@PathVariable id: String): Mono<ClienteVeiculos> = clientes.comVeiculos(id)
}
