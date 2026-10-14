package com.clau.service_track.bff.infra.client.catalogo

import com.clau.service_track.bff.application.port.out.CatalogoPort
import com.clau.service_track.bff.domain.model.Insumo
import com.clau.service_track.bff.domain.model.SaldoDeInsumo
import com.clau.service_track.bff.domain.model.Servico
import com.clau.service_track.bff.infra.client.CallGuard
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.retry.Retry
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
@Profile("!dev")
class CatalogoHttpAdapter(
    private val apiClient: CatalogoApiClient,
    catalogoRetry: Retry,
    catalogoCircuitBreaker: CircuitBreaker,
) : CatalogoPort {

    private val guard = CallGuard("catalogo", catalogoRetry, catalogoCircuitBreaker)

    override fun listarServicos(): Flux<Servico> =
        guard.fluxOrEmpty(SERVICOS_ROUTE) { apiClient.listarServicos() }
            .filter { it.ativo }
            .map(::toServico)

    override fun listarInsumos(): Flux<Insumo> =
        guard.fluxOrEmpty(INSUMOS_ROUTE) { apiClient.listarInsumos() }
            .filter { it.ativo }
            .map(::toInsumo)

    override fun buscarServicos(ids: Set<String>): Mono<Map<String, Servico>> = buscarEmLote(ids) { id ->
        guard.mono("$SERVICOS_ROUTE/{id}") { apiClient.buscarServico(id) }.map(::toServico)
    }

    override fun buscarInsumos(ids: Set<String>): Mono<Map<String, Insumo>> = buscarEmLote(ids) { id ->
        guard.mono("$INSUMOS_ROUTE/{id}") { apiClient.buscarInsumo(id) }.map(::toInsumo)
    }

    override fun saldoDe(insumoId: String): Mono<SaldoDeInsumo> =
        guard.mono("$INSUMOS_ROUTE/{id}/estoque") { apiClient.saldoDe(insumoId) }.map(::toSaldo)

    private fun <T : Any> buscarEmLote(ids: Set<String>, busca: (String) -> Mono<T>): Mono<Map<String, T>> {
        if (ids.isEmpty()) return Mono.just(emptyMap())

        return Flux.fromIterable(ids)
            .flatMap({ id -> busca(id).map { id to it } }, CONCORRENCIA)
            .collectMap({ it.first }, { it.second })
    }

    private fun toServico(resposta: ServicoResponse) = Servico(
        id = resposta.id,
        nome = resposta.nome,
        valorReferencia = resposta.valorReferencia,
    )

    private fun toInsumo(resposta: InsumoResponse) = Insumo(
        id = resposta.id,
        sku = resposta.sku,
        nome = resposta.nome,
        unidadeDeMedida = resposta.unidadeDeMedida,
        custo = resposta.custo,
    )

    private fun toSaldo(resposta: SaldoDeInsumoResponse) = SaldoDeInsumo(
        insumoId = resposta.insumoId,
        sku = resposta.sku,
        unidadeDeMedida = resposta.unidadeDeMedida,
        quantidadeDisponivel = resposta.quantidadeDisponivel,
        quantidadeReservada = resposta.quantidadeReservada,
    )

    private companion object {
        const val SERVICOS_ROUTE = "/servicos"
        const val INSUMOS_ROUTE = "/insumos"
        const val CONCORRENCIA = 4
    }
}
