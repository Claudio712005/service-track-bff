package com.clau.service_track.bff.infra.client.ordens

import com.clau.service_track.bff.application.port.out.OrdensPort
import com.clau.service_track.bff.domain.model.FatoDaOrdem
import com.clau.service_track.bff.domain.model.ItemDeInsumo
import com.clau.service_track.bff.domain.model.ItemDeServico
import com.clau.service_track.bff.domain.model.Orcamento
import com.clau.service_track.bff.domain.model.OrdemServico
import com.clau.service_track.bff.domain.model.PassoDaSaga
import com.clau.service_track.bff.domain.model.PedidoDeAbertura
import com.clau.service_track.bff.domain.model.Saga
import com.clau.service_track.bff.domain.model.StatusDaOrdem
import com.clau.service_track.bff.infra.client.ProtecaoDeChamada
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.retry.Retry
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
@Profile("!dev")
class OrdensHttpAdapter(
    private val cliente: OrdensApiClient,
    retryDeOrdens: Retry,
    disjuntorDeOrdens: CircuitBreaker,
) : OrdensPort {

    private val protecao = ProtecaoDeChamada("ordens", retryDeOrdens, disjuntorDeOrdens)

    override fun abrir(pedido: PedidoDeAbertura): Mono<OrdemServico> =
        protecao.mono(ROTA) { cliente.abrir(paraRequisicao(pedido)) }.map(::paraOrdem)

    override fun buscarPorId(id: String): Mono<OrdemServico> =
        protecao.mono("$ROTA/{id}") { cliente.buscarPorId(id) }.map(::paraOrdem)

    override fun listarDoCliente(clienteId: String): Flux<OrdemServico> =
        protecao.mono(ROTA) { cliente.listarDoCliente(clienteId, TAMANHO_DA_PAGINA) }
            .flatMapIterable { pagina -> pagina.conteudo }
            .map(::paraOrdem)

    override fun historicoDe(id: String): Flux<FatoDaOrdem> =
        protecao.flux("$ROTA/{id}/historico") { cliente.historicoDe(id) }.map(::paraFato)

    override fun sagasDe(id: String): Flux<Saga> =
        protecao.fluxTolerante("$ROTA/{id}/saga") { cliente.sagasDe(id) }.map(::paraSaga)

    override fun aprovarOrcamento(id: String): Mono<OrdemServico> =
        protecao.mono("$ROTA/{id}/orcamento/aprovacao") { cliente.aprovarOrcamento(id) }.map(::paraOrdem)

    private fun paraRequisicao(pedido: PedidoDeAbertura) = AbrirOrdemRequest(
        motivo = pedido.motivo,
        clienteId = pedido.clienteId,
        mecanicoId = pedido.mecanicoId,
        veiculoId = pedido.veiculoId,
        observacao = pedido.observacao,
        prazoConclusao = pedido.prazoConclusao,
    )

    private fun paraOrdem(resposta: OrdemServicoResponse) = OrdemServico(
        id = resposta.id,
        motivo = resposta.motivo,
        observacao = resposta.observacao,
        status = StatusDaOrdem.de(resposta.status),
        clienteId = resposta.clienteId,
        mecanicoId = resposta.mecanicoId,
        veiculoId = resposta.veiculoId,
        prazoConclusao = resposta.prazoConclusao,
        orcamento = resposta.orcamento?.let(::paraOrcamento),
        itensServico = resposta.itensServico.map(::paraItemDeServico),
        itensInsumo = resposta.itensInsumo.map(::paraItemDeInsumo),
        dataCriacao = resposta.dataCriacao,
        dataAtualizacao = resposta.dataAtualizacao,
    )

    private fun paraOrcamento(resposta: OrcamentoResponse) = Orcamento(
        id = resposta.id,
        custoMaoDeObra = resposta.custoMaoDeObra,
        custoInsumos = resposta.custoInsumos,
        valorTotal = resposta.valorTotal,
        aprovado = resposta.aprovado,
    )

    private fun paraItemDeServico(resposta: ItemDeServicoResponse) = ItemDeServico(
        id = resposta.id,
        servicoId = resposta.servicoId,
        servico = null,
        valor = resposta.valor,
        feito = resposta.feito,
    )

    private fun paraItemDeInsumo(resposta: ItemDeInsumoResponse) = ItemDeInsumo(
        id = resposta.id,
        insumoId = resposta.insumoId,
        insumo = null,
        quantidade = resposta.quantidade,
    )

    private fun paraFato(resposta: FatoDaOrdemResponse) = FatoDaOrdem(
        statusAnterior = resposta.statusAnterior,
        statusNovo = resposta.statusNovo,
        transicionou = resposta.transicionou,
        motivo = resposta.motivo,
        ocorridoEm = resposta.ocorridoEm,
    )

    private fun paraSaga(resposta: SagaResponse) = Saga(
        tipo = resposta.tipo,
        situacao = resposta.situacao,
        etapa = resposta.etapa,
        motivo = resposta.motivo,
        passos = resposta.passos.map(::paraPasso),
    )

    private fun paraPasso(resposta: PassoDaSagaResponse) = PassoDaSaga(
        insumoId = resposta.insumoId,
        etapa = resposta.etapa,
        quantidade = resposta.quantidade,
        situacao = resposta.situacao,
        motivo = resposta.motivo,
    )

    private companion object {
        const val ROTA = "/ordens"
        const val TAMANHO_DA_PAGINA = 50
    }
}
