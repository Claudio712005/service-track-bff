package com.clau.service_track.bff.infra.client.fake

import com.clau.service_track.bff.application.port.out.CatalogoPort
import com.clau.service_track.bff.domain.exception.RecursoNaoEncontradoException
import com.clau.service_track.bff.domain.model.Insumo
import com.clau.service_track.bff.domain.model.SaldoDeInsumo
import com.clau.service_track.bff.domain.model.Servico
import java.math.BigDecimal
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
@Profile("dev")
class CatalogoFakeAdapter : CatalogoPort {

    private val log = LoggerFactory.getLogger(CatalogoFakeAdapter::class.java)

    init {
        log.warn("perfil dev ativo: o catalogo esta mocado em memoria, nada sai pela rede")
    }

    override fun listarServicos(): Flux<Servico> = Flux.fromIterable(SERVICOS.values)

    override fun listarInsumos(): Flux<Insumo> = Flux.fromIterable(INSUMOS.values)

    override fun buscarServicos(ids: Set<String>): Mono<Map<String, Servico>> =
        Mono.just(SERVICOS.filterKeys { it in ids })

    override fun buscarInsumos(ids: Set<String>): Mono<Map<String, Insumo>> =
        Mono.just(INSUMOS.filterKeys { it in ids })

    override fun saldoDe(insumoId: String): Mono<SaldoDeInsumo> {
        val insumo = INSUMOS[insumoId]
            ?: return Mono.error(RecursoNaoEncontradoException("Insumo $insumoId nao encontrado"))

        val disponivel = if (insumoId == INSUMO_SEM_SALDO) BigDecimal.ZERO else BigDecimal("48")

        return Mono.just(
            SaldoDeInsumo(
                insumoId = insumo.id,
                sku = insumo.sku,
                unidadeDeMedida = insumo.unidadeDeMedida,
                quantidadeDisponivel = disponivel,
                quantidadeReservada = BigDecimal.ZERO,
            )
        )
    }

    companion object {

        const val SERVICO_DE_OLEO = "bbfdb1a8-66e2-4292-a6cb-6a6d3fb080fd"
        const val INSUMO_DE_OLEO = "018f30bb-77a1-7c22-9b10-2a44de81f0aa"

        const val INSUMO_SEM_SALDO = "018f30d0-9e11-7b44-9c55-3ad2f1b0e7c8"

        val SERVICOS = mapOf(
            SERVICO_DE_OLEO to Servico(
                id = SERVICO_DE_OLEO,
                nome = "Troca de oleo e filtro",
                valorReferencia = BigDecimal("189.90"),
            ),
        )

        val INSUMOS = mapOf(
            INSUMO_DE_OLEO to Insumo(
                id = INSUMO_DE_OLEO,
                sku = "OL-5W30-SYN-1L",
                nome = "Oleo sintetico 5W30",
                unidadeDeMedida = "LITRO",
                custo = BigDecimal("38.90"),
            ),
            INSUMO_SEM_SALDO to Insumo(
                id = INSUMO_SEM_SALDO,
                sku = "OL-20W50-MIN-1L",
                nome = "Oleo 20W50 mineral",
                unidadeDeMedida = "LITRO",
                custo = BigDecimal("24.50"),
            ),
        )
    }
}
