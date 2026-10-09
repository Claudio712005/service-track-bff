package com.clau.service_track.bff.application.service

import com.clau.service_track.bff.application.port.out.CatalogoPort
import com.clau.service_track.bff.application.port.out.OrdensPort
import com.clau.service_track.bff.application.port.out.UsuariosPort
import com.clau.service_track.bff.domain.exception.ServicoIndisponivelException
import com.clau.service_track.bff.domain.model.FatoDaOrdem
import com.clau.service_track.bff.domain.model.OrdemComposta
import com.clau.service_track.bff.domain.model.OrdemServico
import com.clau.service_track.bff.domain.model.PedidoDeAbertura
import com.clau.service_track.bff.domain.model.Saga
import com.clau.service_track.bff.domain.model.Veiculo
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Service
class OrdensService(
    private val ordens: OrdensPort,
    private val usuarios: UsuariosPort,
    private val catalogo: CatalogoPort,
) {

    private val log = LoggerFactory.getLogger(OrdensService::class.java)

    fun abrir(pedido: PedidoDeAbertura): Mono<OrdemServico> = ordens.abrir(pedido)

    fun aprovarOrcamento(id: String): Mono<OrdemServico> = ordens.aprovarOrcamento(id)

    fun historicoDe(id: String): Flux<FatoDaOrdem> = ordens.historicoDe(id)

    fun sagasDe(id: String): Flux<Saga> = ordens.sagasDe(id)

    fun detalhar(id: String): Mono<OrdemComposta> = ordens.buscarPorId(id).flatMap(::compor)

    fun listarDoCliente(clienteId: String): Flux<OrdemServico> = ordens.listarDoCliente(clienteId)

    private fun compor(ordem: OrdemServico): Mono<OrdemComposta> = Mono.zip(
        parcial(USUARIOS) { usuarios.buscarPorId(ordem.clienteId) },
        parcial(USUARIOS) { usuarios.buscarPorId(ordem.mecanicoId) },
        parcial(USUARIOS) { veiculoDe(ordem) },
        parcial(CATALOGO) { catalogo.buscarServicos(ordem.itensServico.map { it.servicoId }.toSet()) },
        parcial(CATALOGO) { catalogo.buscarInsumos(ordem.itensInsumo.map { it.insumoId }.toSet()) },
    ).map { partes ->
        val servicos = partes.t4.valor.orEmpty()
        val insumos = partes.t5.valor.orEmpty()

        val indisponiveis = listOf(partes.t1, partes.t2, partes.t3, partes.t4, partes.t5)
            .mapNotNull { it.servicoIndisponivel }
            .distinct()

        if (indisponiveis.isNotEmpty()) {
            log.warn(
                "ordem composta com parcial indisponivel ordemServicoId={} servicos={}",
                ordem.id, indisponiveis,
            )
        }

        OrdemComposta(
            ordem = ordem.copy(
                itensServico = ordem.itensServico.map { item -> item.copy(servico = servicos[item.servicoId]) },
                itensInsumo = ordem.itensInsumo.map { item -> item.copy(insumo = insumos[item.insumoId]) },
            ),
            cliente = partes.t1.valor,
            mecanico = partes.t2.valor,
            veiculo = partes.t3.valor,
            parciaisIndisponiveis = indisponiveis,
        )
    }

    private fun veiculoDe(ordem: OrdemServico): Mono<Veiculo> =
        usuarios.listarVeiculosDoCliente(ordem.clienteId)
            .filter { it.id == ordem.veiculoId }
            .next()

    private fun <T : Any> parcial(servico: String, chamada: () -> Mono<T>): Mono<Parcial<T>> =
        chamada()
            .map { Parcial(valor = it) }
            .defaultIfEmpty(Parcial())
            .onErrorResume(ServicoIndisponivelException::class.java) {
                Mono.just(Parcial(servicoIndisponivel = servico))
            }

    private data class Parcial<T : Any>(
        val valor: T? = null,
        val servicoIndisponivel: String? = null,
    )

    private companion object {
        const val USUARIOS = "usuarios"
        const val CATALOGO = "catalogo"
    }
}
