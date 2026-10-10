package com.clau.service_track.bff.infra.web.controller

import com.clau.service_track.bff.application.port.`in`.AbrirOrdemRequest
import com.clau.service_track.bff.application.port.`in`.OrdemApiPort
import com.clau.service_track.bff.application.service.OrdensService
import com.clau.service_track.bff.domain.exception.RecursoNaoEncontradoException
import com.clau.service_track.bff.domain.model.FatoDaOrdem
import com.clau.service_track.bff.domain.model.OrdemComposta
import com.clau.service_track.bff.domain.model.OrdemServico
import com.clau.service_track.bff.domain.model.Saga
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RestController
class OrdemController(
    private val ordens: OrdensService,
) : OrdemApiPort {

    override fun abrir(requisicao: AbrirOrdemRequest): Mono<ResponseEntity<OrdemServico>> =
        ordens.abrir(requisicao.paraPedido())
            .map { ordem -> ResponseEntity.status(HttpStatus.CREATED).body(ordem) }

    override fun detalhar(id: String): Mono<OrdemComposta> = ordens.detalhar(id)
        .switchIfEmpty(Mono.error { RecursoNaoEncontradoException("Ordem de servico $id nao encontrada") })

    override fun listarDoCliente(clienteId: String): Flux<OrdemServico> = ordens.listarDoCliente(clienteId)

    override fun aprovarOrcamento(id: String): Mono<OrdemServico> = ordens.aprovarOrcamento(id)

    override fun historico(id: String): Flux<FatoDaOrdem> = ordens.historicoDe(id)

    override fun saga(id: String): Flux<Saga> = ordens.sagasDe(id)
}
