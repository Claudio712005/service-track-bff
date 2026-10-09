package com.clau.service_track.bff.infra.client.catalogo

import com.clau.service_track.bff.application.port.out.CatalogoPort
import com.clau.service_track.bff.domain.model.Insumo
import com.clau.service_track.bff.domain.model.SaldoDeInsumo
import com.clau.service_track.bff.domain.model.Servico
import com.clau.service_track.bff.infra.client.ProtecaoDeChamada
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.retry.Retry
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
@Profile("!dev")
class CatalogoHttpAdapter(
    private val cliente: CatalogoApiClient,
    retryDeCatalogo: Retry,
    disjuntorDeCatalogo: CircuitBreaker,
) : CatalogoPort {

    private val protecao = ProtecaoDeChamada("catalogo", retryDeCatalogo, disjuntorDeCatalogo)

    override fun listarServicos(): Flux<Servico> =
        protecao.fluxTolerante(ROTA_DE_SERVICOS) { cliente.listarServicos() }
            .filter { it.ativo }
            .map(::paraServico)

    override fun listarInsumos(): Flux<Insumo> =
        protecao.fluxTolerante(ROTA_DE_INSUMOS) { cliente.listarInsumos() }
            .filter { it.ativo }
            .map(::paraInsumo)

    override fun buscarServicos(ids: Set<String>): Mono<Map<String, Servico>> = buscarEmLote(ids) { id ->
        protecao.mono("$ROTA_DE_SERVICOS/{id}") { cliente.buscarServico(id) }.map(::paraServico)
    }

    override fun buscarInsumos(ids: Set<String>): Mono<Map<String, Insumo>> = buscarEmLote(ids) { id ->
        protecao.mono("$ROTA_DE_INSUMOS/{id}") { cliente.buscarInsumo(id) }.map(::paraInsumo)
    }

    override fun saldoDe(insumoId: String): Mono<SaldoDeInsumo> =
        protecao.mono("$ROTA_DE_INSUMOS/{id}/estoque") { cliente.saldoDe(insumoId) }.map(::paraSaldo)

    private fun <T : Any> buscarEmLote(ids: Set<String>, busca: (String) -> Mono<T>): Mono<Map<String, T>> {
        if (ids.isEmpty()) return Mono.just(emptyMap())

        return Flux.fromIterable(ids)
            .flatMap({ id -> busca(id).map { id to it } }, CONCORRENCIA)
            .collectMap({ it.first }, { it.second })
    }

    private fun paraServico(resposta: ServicoResponse) = Servico(
        id = resposta.id,
        nome = resposta.nome,
        valorReferencia = resposta.valorReferencia,
    )

    private fun paraInsumo(resposta: InsumoResponse) = Insumo(
        id = resposta.id,
        sku = resposta.sku,
        nome = resposta.nome,
        unidadeDeMedida = resposta.unidadeDeMedida,
        custo = resposta.custo,
    )

    private fun paraSaldo(resposta: SaldoDeInsumoResponse) = SaldoDeInsumo(
        insumoId = resposta.insumoId,
        sku = resposta.sku,
        unidadeDeMedida = resposta.unidadeDeMedida,
        quantidadeDisponivel = resposta.quantidadeDisponivel,
        quantidadeReservada = resposta.quantidadeReservada,
    )

    private companion object {
        const val ROTA_DE_SERVICOS = "/servicos"
        const val ROTA_DE_INSUMOS = "/insumos"
        const val CONCORRENCIA = 4
    }
}
