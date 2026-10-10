package com.clau.service_track.bff.infra.client.ordens

import com.clau.service_track.bff.domain.exception.RecursoNaoEncontradoException
import com.clau.service_track.bff.domain.exception.ServicoIndisponivelException
import com.clau.service_track.bff.domain.model.PedidoDeAbertura
import com.clau.service_track.bff.domain.model.StatusDaOrdem
import com.clau.service_track.bff.infra.config.FabricaDeResiliencia
import com.clau.service_track.bff.infra.config.ResilienceProperties
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class OrdensHttpAdapterTest {

    private val chamadas = AtomicInteger()
    private val pedidos = mutableListOf<AbrirOrdemRequest>()

    private val properties = ResilienceProperties(
        maxAttempts = 3,
        initialBackoff = Duration.ofMillis(1),
        backoffMultiplier = 2.0,
        maxBackoff = Duration.ofMillis(5),
        slidingWindowSize = 100,
        minimumNumberOfCalls = 100,
    )

    private fun erroHttp(status: HttpStatus) = WebClientResponseException(
        status.value(),
        status.reasonPhrase,
        null,
        null,
        null,
    )

    private fun adaptador(
        aberta: () -> Mono<OrdemServicoResponse> = { Mono.just(ordem()) },
        porId: () -> Mono<OrdemServicoResponse> = { Mono.just(ordem()) },
        pagina: () -> Mono<PaginaDeOrdensResponse> = { Mono.just(PaginaDeOrdensResponse(listOf(ordem()), 1)) },
        historico: () -> Flux<FatoDaOrdemResponse> = { Flux.just(fato()) },
        sagas: () -> Flux<SagaResponse> = { Flux.just(saga()) },
        aprovada: () -> Mono<OrdemServicoResponse> = { Mono.just(ordem()) },
    ): OrdensHttpAdapter {
        val cliente = object : OrdensApiClient {
            override fun abrir(requisicao: AbrirOrdemRequest): Mono<OrdemServicoResponse> {
                chamadas.incrementAndGet()
                pedidos.add(requisicao)
                return aberta()
            }

            override fun buscarPorId(id: String): Mono<OrdemServicoResponse> {
                chamadas.incrementAndGet()
                return porId()
            }

            override fun listarDoCliente(clienteId: String, tamanho: Int): Mono<PaginaDeOrdensResponse> {
                chamadas.incrementAndGet()
                return pagina()
            }

            override fun historicoDe(id: String): Flux<FatoDaOrdemResponse> {
                chamadas.incrementAndGet()
                return historico()
            }

            override fun sagasDe(id: String): Flux<SagaResponse> {
                chamadas.incrementAndGet()
                return sagas()
            }

            override fun aprovarOrcamento(id: String): Mono<OrdemServicoResponse> {
                chamadas.incrementAndGet()
                return aprovada()
            }
        }

        val fabrica = FabricaDeResiliencia(properties)
        return OrdensHttpAdapter(
            cliente = cliente,
            retryDeOrdens = fabrica.retry("ordens"),
            disjuntorDeOrdens = fabrica.circuitBreaker("ordens"),
        )
    }

    private fun ordem() = OrdemServicoResponse(
        id = "018f30bb-0000-7c22-9b10-2a44de81f0aa",
        motivo = "barulho na suspensao",
        observacao = "cliente aguarda",
        clienteId = "018f2c9a-5f2e-7c31-9a41-6f3b2d0e9c11",
        mecanicoId = "018f2ca1-2b77-7f10-8c02-91ab7d4e5f20",
        veiculoId = "018f30c4-1d55-7a98-8f03-7bb1c2e4d5f6",
        status = "AGUARDANDO_APROVACAO",
        prazoConclusao = LocalDateTime.of(2030, 1, 1, 10, 0),
        orcamento = OrcamentoResponse(
            id = "orc-1",
            custoMaoDeObra = BigDecimal("189.90"),
            custoInsumos = BigDecimal("77.80"),
            valorTotal = BigDecimal("267.70"),
            aprovado = false,
        ),
        itensServico = listOf(ItemDeServicoResponse("is-1", "s-1", BigDecimal("189.90"), false)),
        itensInsumo = listOf(ItemDeInsumoResponse("ii-1", "i-1", BigDecimal("2"))),
        dataCriacao = LocalDateTime.of(2026, 10, 8, 9, 0),
        dataAtualizacao = LocalDateTime.of(2026, 10, 8, 9, 5),
    )

    private fun fato() = FatoDaOrdemResponse(
        statusAnterior = "EM_EXECUCAO",
        statusNovo = "EM_EXECUCAO",
        transicionou = false,
        motivo = "ordem bloqueada na etapa CONSUMO_DE_INSUMOS",
        ocorridoEm = OffsetDateTime.parse("2026-10-08T12:00:00Z"),
    )

    private fun saga() = SagaResponse(
        tipo = "RESERVA",
        situacao = "COMPENSANDO",
        etapa = "LIBERACAO_DE_INSUMOS",
        motivo = "solicitado 2, disponivel 0",
        passos = listOf(PassoDaSagaResponse("i-1", "LIBERACAO_DE_INSUMOS", BigDecimal("2"), "PEDIDO")),
    )

    @Test
    fun `mapeia a ordem inteira para o dominio`() {
        StepVerifier.create(adaptador().buscarPorId("qualquer"))
            .assertNext { ordem ->
                assertEquals(StatusDaOrdem.AGUARDANDO_APROVACAO, ordem.status)
                assertEquals(0, ordem.orcamento!!.valorTotal.compareTo(BigDecimal("267.70")))
                assertEquals(1, ordem.itensServico.size)
                assertEquals(1, ordem.itensInsumo.size)
                assertNull(ordem.itensServico.single().servico, "o servico e resolvido na composicao, nao aqui")
                assertNull(ordem.itensInsumo.single().insumo, "o insumo e resolvido na composicao, nao aqui")
            }
            .verifyComplete()
    }

    @Test
    fun `status desconhecido e recusado em vez de virar nulo`() {
        val adaptador = adaptador(porId = { Mono.just(ordem().copy(status = "EM_LAVAGEM")) })

        StepVerifier.create(adaptador.buscarPorId("qualquer"))
            .expectError(IllegalArgumentException::class.java)
            .verify()
    }

    @Test
    fun `abertura leva o pedido para o contrato do servico de ordens`() {
        val pedido = PedidoDeAbertura(
            motivo = "revisao de 10 mil",
            clienteId = "c-1",
            mecanicoId = "m-1",
            veiculoId = "v-1",
            observacao = "sem observacao",
            prazoConclusao = null,
        )

        StepVerifier.create(adaptador().abrir(pedido)).expectNextCount(1).verifyComplete()

        assertEquals("revisao de 10 mil", pedidos.single().motivo)
        assertEquals("c-1", pedidos.single().clienteId)
    }

    @Test
    fun `listagem do cliente achata a pagina em fluxo`() {
        val adaptador = adaptador(
            pagina = { Mono.just(PaginaDeOrdensResponse(listOf(ordem(), ordem().copy(id = "outra")), 2)) },
        )

        StepVerifier.create(adaptador.listarDoCliente("c-1")).expectNextCount(2).verifyComplete()
    }

    @Test
    fun `historico preserva o fato sem transicao`() {
        StepVerifier.create(adaptador().historicoDe("qualquer"))
            .assertNext { fato ->
                assertTrue(!fato.transicionou, "bloqueio nao e transicao e o cliente precisa distinguir")
                assertEquals("EM_EXECUCAO", fato.statusNovo)
            }
            .verifyComplete()
    }

    @Test
    fun `saga mapeia etapa, situacao e passos`() {
        StepVerifier.create(adaptador().sagasDe("qualquer"))
            .assertNext { saga ->
                assertEquals("COMPENSANDO", saga.situacao)
                assertEquals("LIBERACAO_DE_INSUMOS", saga.etapa)
                assertEquals("PEDIDO", saga.passos.single().situacao)
            }
            .verifyComplete()
    }

    @Test
    fun `saga ausente devolve vazio, nao erro`() {
        val adaptador = adaptador(sagas = { Flux.error(erroHttp(HttpStatus.NOT_FOUND)) })

        StepVerifier.create(adaptador.sagasDe("qualquer")).verifyComplete()
    }

    @Test
    fun `ordem inexistente propaga recurso nao encontrado`() {
        val adaptador = adaptador(porId = { Mono.error(erroHttp(HttpStatus.NOT_FOUND)) })

        StepVerifier.create(adaptador.buscarPorId("inexistente"))
            .expectError(RecursoNaoEncontradoException::class.java)
            .verify()
    }

    @Test
    fun `erro de servidor no servico de ordens e retentado e vira indisponibilidade`() {
        chamadas.set(0)
        val adaptador = adaptador(aprovada = { Mono.error(erroHttp(HttpStatus.BAD_GATEWAY)) })

        StepVerifier.create(adaptador.aprovarOrcamento("qualquer"))
            .expectError(ServicoIndisponivelException::class.java)
            .verify()

        assertEquals(3, chamadas.get())
    }
}
