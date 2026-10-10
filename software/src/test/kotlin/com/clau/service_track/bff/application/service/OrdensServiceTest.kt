package com.clau.service_track.bff.application.service

import com.clau.service_track.bff.application.port.out.CatalogoPort
import com.clau.service_track.bff.application.port.out.OrdensPort
import com.clau.service_track.bff.application.port.out.UsuariosPort
import com.clau.service_track.bff.domain.exception.RecursoNaoEncontradoException
import com.clau.service_track.bff.domain.exception.ServicoIndisponivelException
import com.clau.service_track.bff.domain.model.FatoDaOrdem
import com.clau.service_track.bff.domain.model.Insumo
import com.clau.service_track.bff.domain.model.ItemDeInsumo
import com.clau.service_track.bff.domain.model.ItemDeServico
import com.clau.service_track.bff.domain.model.OrdemServico
import com.clau.service_track.bff.domain.model.PedidoDeAbertura
import com.clau.service_track.bff.domain.model.Pessoa
import com.clau.service_track.bff.domain.model.Saga
import com.clau.service_track.bff.domain.model.SaldoDeInsumo
import com.clau.service_track.bff.domain.model.Servico
import com.clau.service_track.bff.domain.model.StatusDaOrdem
import com.clau.service_track.bff.domain.model.TipoDeUsuario
import com.clau.service_track.bff.domain.model.Veiculo
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class OrdensServiceTest {

    private val cliente = Pessoa("c-1", "52998224725", "Joana", "joana@exemplo.test", TipoDeUsuario.CLIENTE)
    private val mecanico = Pessoa("m-1", "11144477735", "Rui", "rui@exemplo.test", TipoDeUsuario.MECANICO)
    private val veiculo = Veiculo("v-1", "ABC1D23", "Fiat", "Argo", 2022)
    private val servico = Servico("s-1", "Troca de oleo", BigDecimal("189.90"))
    private val insumo = Insumo("i-1", "OL-5W30", "Oleo 5W30", "LITRO", BigDecimal("38.90"))

    private val ordem = OrdemServico(
        id = "o-1",
        motivo = "barulho na suspensao",
        observacao = "",
        status = StatusDaOrdem.AGUARDANDO_APROVACAO,
        clienteId = "c-1",
        mecanicoId = "m-1",
        veiculoId = "v-1",
        prazoConclusao = null,
        orcamento = null,
        itensServico = listOf(ItemDeServico("is-1", "s-1", null, BigDecimal("189.90"), false)),
        itensInsumo = listOf(ItemDeInsumo("ii-1", "i-1", null, BigDecimal("2"))),
        dataCriacao = LocalDateTime.now(),
        dataAtualizacao = LocalDateTime.now(),
    )

    private fun servico(
        usuarios: UsuariosPort = usuariosInteiro(),
        catalogo: CatalogoPort = catalogoInteiro(),
        ordens: OrdensPort = ordensInteiro(),
    ) = OrdensService(ordens, usuarios, catalogo)

    private fun usuariosInteiro(
        porId: (String) -> Mono<Pessoa> = { id -> Mono.just(if (id == "c-1") cliente else mecanico) },
        veiculos: () -> Flux<Veiculo> = { Flux.just(veiculo) },
    ) = object : UsuariosPort {
        override fun listarPorTipo(tipo: TipoDeUsuario): Flux<Pessoa> = Flux.empty()
        override fun buscarPorId(id: String): Mono<Pessoa> = porId(id)
        override fun listarVeiculosDoCliente(clienteId: String): Flux<Veiculo> = veiculos()
    }

    private fun catalogoInteiro(
        servicos: () -> Mono<Map<String, Servico>> = { Mono.just(mapOf("s-1" to servico)) },
        insumos: () -> Mono<Map<String, Insumo>> = { Mono.just(mapOf("i-1" to insumo)) },
    ) = object : CatalogoPort {
        override fun listarServicos(): Flux<Servico> = Flux.just(servico)
        override fun listarInsumos(): Flux<Insumo> = Flux.just(insumo)
        override fun buscarServicos(ids: Set<String>): Mono<Map<String, Servico>> = servicos()
        override fun buscarInsumos(ids: Set<String>): Mono<Map<String, Insumo>> = insumos()
        override fun saldoDe(insumoId: String): Mono<SaldoDeInsumo> = Mono.empty()
    }

    private fun ordensInteiro(
        porId: () -> Mono<OrdemServico> = { Mono.just(ordem) },
    ) = object : OrdensPort {
        override fun abrir(pedido: PedidoDeAbertura): Mono<OrdemServico> = Mono.just(ordem)
        override fun buscarPorId(id: String): Mono<OrdemServico> = porId()
        override fun listarDoCliente(clienteId: String): Flux<OrdemServico> = Flux.just(ordem)
        override fun historicoDe(id: String): Flux<FatoDaOrdem> = Flux.empty()
        override fun sagasDe(id: String): Flux<Saga> = Flux.empty()
        override fun aprovarOrcamento(id: String): Mono<OrdemServico> = Mono.just(ordem)
    }

    @Test
    fun `compoe a ordem com cliente, mecanico, veiculo, servico e insumo resolvidos`() {
        StepVerifier.create(servico().detalhar("o-1"))
            .assertNext { composta ->
                assertEquals("Joana", composta.cliente?.nome)
                assertEquals("Rui", composta.mecanico?.nome)
                assertEquals("ABC1D23", composta.veiculo?.placa)
                assertEquals("Troca de oleo", composta.ordem.itensServico.single().servico?.nome)
                assertEquals("OL-5W30", composta.ordem.itensInsumo.single().insumo?.sku)
                assertTrue(composta.parciaisIndisponiveis.isEmpty())
            }
            .verifyComplete()
    }

    @Test
    fun `usuarios indisponivel nao derruba a ordem, e aparece como parcial ausente`() {
        val usuarios = usuariosInteiro(porId = { Mono.error(ServicoIndisponivelException("usuarios")) })

        StepVerifier.create(servico(usuarios = usuarios).detalhar("o-1"))
            .assertNext { composta ->
                assertNull(composta.cliente)
                assertNull(composta.mecanico)
                assertEquals(listOf("usuarios"), composta.parciaisIndisponiveis)
                assertEquals("Troca de oleo", composta.ordem.itensServico.single().servico?.nome)
            }
            .verifyComplete()
    }

    @Test
    fun `catalogo indisponivel deixa o item sem nome, sem perder o identificador`() {
        val catalogo = catalogoInteiro(
            servicos = { Mono.error(ServicoIndisponivelException("catalogo")) },
            insumos = { Mono.error(ServicoIndisponivelException("catalogo")) },
        )

        StepVerifier.create(servico(catalogo = catalogo).detalhar("o-1"))
            .assertNext { composta ->
                assertEquals(listOf("catalogo"), composta.parciaisIndisponiveis)
                assertNull(composta.ordem.itensServico.single().servico)
                assertEquals("s-1", composta.ordem.itensServico.single().servicoId)
                assertEquals("Joana", composta.cliente?.nome)
            }
            .verifyComplete()
    }

    @Test
    fun `dois servicos indisponiveis aparecem uma vez cada`() {
        val usuarios = usuariosInteiro(porId = { Mono.error(ServicoIndisponivelException("usuarios")) })
        val catalogo = catalogoInteiro(
            servicos = { Mono.error(ServicoIndisponivelException("catalogo")) },
            insumos = { Mono.error(ServicoIndisponivelException("catalogo")) },
        )

        StepVerifier.create(servico(usuarios = usuarios, catalogo = catalogo).detalhar("o-1"))
            .assertNext { composta ->
                assertEquals(setOf("usuarios", "catalogo"), composta.parciaisIndisponiveis.toSet())
                assertEquals(2, composta.parciaisIndisponiveis.size)
            }
            .verifyComplete()
    }

    @Test
    fun `veiculo que nao e o da ordem nao entra na composicao`() {
        val usuarios = usuariosInteiro(veiculos = { Flux.just(veiculo.copy(id = "outro")) })

        StepVerifier.create(servico(usuarios = usuarios).detalhar("o-1"))
            .assertNext { composta -> assertNull(composta.veiculo) }
            .verifyComplete()
    }

    @Test
    fun `ordem inexistente nao compoe nada`() {
        val ordens = ordensInteiro(porId = { Mono.empty() })

        StepVerifier.create(servico(ordens = ordens).detalhar("inexistente")).verifyComplete()
    }

    @Test
    fun `erro que nao e indisponibilidade sobe, em vez de virar parcial ausente`() {
        val usuarios = usuariosInteiro(porId = { Mono.error(RecursoNaoEncontradoException("cliente")) })

        StepVerifier.create(servico(usuarios = usuarios).detalhar("o-1"))
            .expectError(RecursoNaoEncontradoException::class.java)
            .verify()
    }

    @Test
    fun `abertura, aprovacao, historico e saga apenas repassam`() {
        val pedido = PedidoDeAbertura("motivo", "c-1", "m-1", "v-1", "", null)

        StepVerifier.create(servico().abrir(pedido)).expectNextCount(1).verifyComplete()
        StepVerifier.create(servico().aprovarOrcamento("o-1")).expectNextCount(1).verifyComplete()
        StepVerifier.create(servico().historicoDe("o-1")).verifyComplete()
        StepVerifier.create(servico().sagasDe("o-1")).verifyComplete()
        StepVerifier.create(servico().listarDoCliente("c-1")).expectNextCount(1).verifyComplete()
    }
}
