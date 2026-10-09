package com.clau.service_track.bff.infra.client.fake

import com.clau.service_track.bff.application.port.out.OrdensPort
import com.clau.service_track.bff.domain.exception.RecursoNaoEncontradoException
import com.clau.service_track.bff.domain.model.FatoDaOrdem
import com.clau.service_track.bff.domain.model.ItemDeInsumo
import com.clau.service_track.bff.domain.model.ItemDeServico
import com.clau.service_track.bff.domain.model.Orcamento
import com.clau.service_track.bff.domain.model.OrdemServico
import com.clau.service_track.bff.domain.model.PassoDaSaga
import com.clau.service_track.bff.domain.model.PedidoDeAbertura
import com.clau.service_track.bff.domain.model.Saga
import com.clau.service_track.bff.domain.model.StatusDaOrdem
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
@Profile("dev")
class OrdensFakeAdapter : OrdensPort {

    private val log = LoggerFactory.getLogger(OrdensFakeAdapter::class.java)

    private val ordens = ConcurrentHashMap<String, OrdemServico>()

    init {
        log.warn("perfil dev ativo: o servico de ordens esta mocado em memoria, nada sai pela rede")
    }

    override fun abrir(pedido: PedidoDeAbertura): Mono<OrdemServico> {
        val agora = LocalDateTime.now()
        val id = UUID.randomUUID().toString()

        val ordem = OrdemServico(
            id = id,
            motivo = pedido.motivo,
            observacao = pedido.observacao,
            status = StatusDaOrdem.EM_DIAGNOSTICO,
            clienteId = pedido.clienteId,
            mecanicoId = pedido.mecanicoId,
            veiculoId = pedido.veiculoId,
            prazoConclusao = pedido.prazoConclusao,
            orcamento = Orcamento(
                id = UUID.randomUUID().toString(),
                custoMaoDeObra = BigDecimal("189.90"),
                custoInsumos = BigDecimal("77.80"),
                valorTotal = BigDecimal("267.70"),
                aprovado = false,
            ),
            itensServico = listOf(
                ItemDeServico(
                    id = UUID.randomUUID().toString(),
                    servicoId = CatalogoFakeAdapter.SERVICO_DE_OLEO,
                    servico = null,
                    valor = BigDecimal("189.90"),
                    feito = false,
                ),
            ),
            itensInsumo = listOf(
                ItemDeInsumo(
                    id = UUID.randomUUID().toString(),
                    insumoId = CatalogoFakeAdapter.INSUMO_DE_OLEO,
                    insumo = null,
                    quantidade = BigDecimal("2"),
                ),
            ),
            dataCriacao = agora,
            dataAtualizacao = agora,
        )

        ordens[id] = ordem
        return Mono.just(ordem)
    }

    override fun buscarPorId(id: String): Mono<OrdemServico> = Mono.justOrEmpty(ordens[id])

    override fun listarDoCliente(clienteId: String): Flux<OrdemServico> =
        Flux.fromIterable(ordens.values.filter { it.clienteId == clienteId })

    override fun historicoDe(id: String): Flux<FatoDaOrdem> {
        val ordem = ordens[id] ?: return Flux.error(RecursoNaoEncontradoException("Ordem $id nao encontrada"))

        return Flux.just(
            FatoDaOrdem(null, StatusDaOrdem.RECEBIDA.name, true, "abertura da ordem de servico", agoraUtc()),
            FatoDaOrdem(
                StatusDaOrdem.RECEBIDA.name,
                ordem.status.name,
                true,
                null,
                agoraUtc(),
            ),
        )
    }

    override fun sagasDe(id: String): Flux<Saga> {
        val ordem = ordens[id] ?: return Flux.empty()
        if (ordem.orcamento?.aprovado != true) return Flux.empty()

        return Flux.just(
            Saga(
                tipo = "RESERVA",
                situacao = "EM_CURSO",
                etapa = "RESERVA_DE_INSUMOS",
                motivo = null,
                passos = ordem.itensInsumo.map { item ->
                    PassoDaSaga(
                        insumoId = item.insumoId,
                        etapa = "RESERVA_DE_INSUMOS",
                        quantidade = item.quantidade,
                        situacao = "PEDIDO",
                        motivo = null,
                    )
                },
            ),
        )
    }

    override fun aprovarOrcamento(id: String): Mono<OrdemServico> {
        val ordem = ordens[id] ?: return Mono.error(RecursoNaoEncontradoException("Ordem $id nao encontrada"))

        val aprovada = ordem.copy(
            status = StatusDaOrdem.AGUARDANDO_APROVACAO,
            orcamento = ordem.orcamento?.copy(aprovado = true),
            dataAtualizacao = LocalDateTime.now(),
        )
        ordens[id] = aprovada
        return Mono.just(aprovada)
    }

    private fun agoraUtc(): OffsetDateTime = OffsetDateTime.now()
}
