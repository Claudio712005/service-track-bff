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
import com.clau.service_track.bff.infra.client.CallGuard
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.retry.Retry
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
@Profile("!dev")
class OrdensHttpAdapter(
    private val apiClient: OrdensApiClient,
    ordensRetry: Retry,
    ordensCircuitBreaker: CircuitBreaker,
) : OrdensPort {

    private val guard = CallGuard("ordens", ordensRetry, ordensCircuitBreaker)

    override fun abrir(pedido: PedidoDeAbertura): Mono<OrdemServico> =
        guard.mono(ROUTE) { apiClient.abrir(toRequest(pedido)) }.map(::toOrdem)

    override fun buscarPorId(id: String): Mono<OrdemServico> =
        guard.mono("$ROUTE/{id}") { apiClient.buscarPorId(id) }.map(::toOrdem)

    override fun listarDoCliente(clienteId: String): Flux<OrdemServico> =
        guard.mono(ROUTE) { apiClient.listarDoCliente(clienteId, PAGE_SIZE) }
            .flatMapIterable { pagina -> pagina.conteudo }
            .map(::toOrdem)

    override fun historicoDe(id: String): Flux<FatoDaOrdem> =
        guard.flux("$ROUTE/{id}/historico") { apiClient.historicoDe(id) }.map(::toFato)

    override fun sagasDe(id: String): Flux<Saga> =
        guard.fluxOrEmpty("$ROUTE/{id}/saga") { apiClient.sagasDe(id) }.map(::toSaga)

    override fun aprovarOrcamento(id: String): Mono<OrdemServico> =
        guard.mono("$ROUTE/{id}/orcamento/aprovacao") { apiClient.aprovarOrcamento(id) }.map(::toOrdem)

    private fun toRequest(pedido: PedidoDeAbertura) = AbrirOrdemRequest(
        motivo = pedido.motivo,
        clienteId = pedido.clienteId,
        mecanicoId = pedido.mecanicoId,
        veiculoId = pedido.veiculoId,
        observacao = pedido.observacao,
        prazoConclusao = pedido.prazoConclusao,
    )

    private fun toOrdem(resposta: OrdemServicoResponse) = OrdemServico(
        id = resposta.id,
        motivo = resposta.motivo,
        observacao = resposta.observacao,
        status = StatusDaOrdem.de(resposta.status),
        clienteId = resposta.clienteId,
        mecanicoId = resposta.mecanicoId,
        veiculoId = resposta.veiculoId,
        prazoConclusao = resposta.prazoConclusao,
        orcamento = resposta.orcamento?.let(::paraOrcamento),
        itensServico = resposta.itensServico.map(::toItemDeServico),
        itensInsumo = resposta.itensInsumo.map(::toItemDeInsumo),
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

    private fun toItemDeServico(resposta: ItemDeServicoResponse) = ItemDeServico(
        id = resposta.id,
        servicoId = resposta.servicoId,
        servico = null,
        valor = resposta.valor,
        feito = resposta.feito,
    )

    private fun toItemDeInsumo(resposta: ItemDeInsumoResponse) = ItemDeInsumo(
        id = resposta.id,
        insumoId = resposta.insumoId,
        insumo = null,
        quantidade = resposta.quantidade,
    )

    private fun toFato(resposta: FatoDaOrdemResponse) = FatoDaOrdem(
        statusAnterior = resposta.statusAnterior,
        statusNovo = resposta.statusNovo,
        transicionou = resposta.transicionou,
        motivo = resposta.motivo,
        ocorridoEm = resposta.ocorridoEm,
    )

    private fun toSaga(resposta: SagaResponse) = Saga(
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
        const val ROUTE = "/ordens"
        const val PAGE_SIZE = 50
    }
}
