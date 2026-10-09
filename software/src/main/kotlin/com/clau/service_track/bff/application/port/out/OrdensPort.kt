package com.clau.service_track.bff.application.port.out

import com.clau.service_track.bff.domain.model.FatoDaOrdem
import com.clau.service_track.bff.domain.model.OrdemServico
import com.clau.service_track.bff.domain.model.PedidoDeAbertura
import com.clau.service_track.bff.domain.model.Saga
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface OrdensPort {

    fun abrir(pedido: PedidoDeAbertura): Mono<OrdemServico>

    fun buscarPorId(id: String): Mono<OrdemServico>

    fun listarDoCliente(clienteId: String): Flux<OrdemServico>

    fun historicoDe(id: String): Flux<FatoDaOrdem>

    fun sagasDe(id: String): Flux<Saga>

    fun aprovarOrcamento(id: String): Mono<OrdemServico>
}
