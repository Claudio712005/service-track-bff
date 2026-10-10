package com.clau.service_track.bff.application.port.`in`

import com.clau.service_track.bff.domain.model.Insumo
import com.clau.service_track.bff.domain.model.SaldoDeInsumo
import com.clau.service_track.bff.domain.model.Servico
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Tag(name = "Catálogo", description = "Serviços e insumos que o atendente escolhe ao montar a ordem.")
@RequestMapping("/catalogo")
interface CatalogoApiPort {

    @Operation(
        summary = "Lista os serviços ativos",
        description = "Item inativo no catálogo não chega aqui: ele não pode entrar em orçamento novo."
    )
    @GetMapping("/servicos")
    fun servicos(): Flux<Servico>

    @Operation(summary = "Lista os insumos ativos")
    @GetMapping("/insumos")
    fun insumos(): Flux<Insumo>

    @Operation(
        summary = "Saldo de um insumo",
        description = "É o que diz se vale pedir a reserva. Saldo zero recusa a reserva e faz a saga compensar."
    )
    @GetMapping("/insumos/{id}/saldo")
    fun saldo(@PathVariable id: String): Mono<SaldoDeInsumo>
}
