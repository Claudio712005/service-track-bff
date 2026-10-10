package com.clau.service_track.bff.infra.web.controller

import com.clau.service_track.bff.application.port.`in`.ClienteApiPort
import com.clau.service_track.bff.application.service.ClientesService
import com.clau.service_track.bff.domain.model.ClienteVeiculos
import com.clau.service_track.bff.domain.model.Pessoa
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
class ClienteController(
    private val clientes: ClientesService,
) : ClienteApiPort {

    override fun listar(): Flux<Pessoa> = clientes.listar()

    override fun buscar(id: String): Mono<ClienteVeiculos> = clientes.comVeiculos(id)
}
