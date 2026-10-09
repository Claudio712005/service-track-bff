package com.clau.service_track.bff.infra.client.catalogo

import com.clau.service_track.bff.domain.exception.NaoAutorizadoException
import com.clau.service_track.bff.domain.exception.RecursoNaoEncontradoException
import com.clau.service_track.bff.domain.exception.ServicoIndisponivelException
import com.clau.service_track.bff.infra.config.FabricaDeResiliencia
import com.clau.service_track.bff.infra.config.ResilienceProperties
import java.math.BigDecimal
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class CatalogoHttpAdapterTest {

    private val chamadas = AtomicInteger()

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
        servicos: () -> Flux<ServicoResponse> = { Flux.just(servico()) },
        insumos: () -> Flux<InsumoResponse> = { Flux.just(insumo()) },
        umServico: () -> Mono<ServicoResponse> = { Mono.just(servico()) },
        umInsumo: () -> Mono<InsumoResponse> = { Mono.just(insumo()) },
        oSaldo: () -> Mono<SaldoDeInsumoResponse> = { Mono.just(saldo()) },
    ): CatalogoHttpAdapter {
        val cliente = object : CatalogoApiClient {
            override fun listarServicos(): Flux<ServicoResponse> {
                chamadas.incrementAndGet()
                return servicos()
            }

            override fun listarInsumos(): Flux<InsumoResponse> {
                chamadas.incrementAndGet()
                return insumos()
            }

            override fun buscarServico(id: String): Mono<ServicoResponse> {
                chamadas.incrementAndGet()
                return umServico()
            }

            override fun buscarInsumo(id: String): Mono<InsumoResponse> {
                chamadas.incrementAndGet()
                return umInsumo()
            }

            override fun saldoDe(insumoId: String): Mono<SaldoDeInsumoResponse> {
                chamadas.incrementAndGet()
                return oSaldo()
            }
        }

        val fabrica = FabricaDeResiliencia(properties)
        return CatalogoHttpAdapter(
            cliente = cliente,
            retryDeCatalogo = fabrica.retry("catalogo"),
            disjuntorDeCatalogo = fabrica.circuitBreaker("catalogo"),
        )
    }

    private fun servico() = ServicoResponse(
        id = "bbfdb1a8-66e2-4292-a6cb-6a6d3fb080fd",
        nome = "Troca de oleo e filtro",
        valorReferencia = BigDecimal("189.90"),
    )

    private fun insumo() = InsumoResponse(
        id = "018f30bb-77a1-7c22-9b10-2a44de81f0aa",
        sku = "OL-5W30-SYN-1L",
        nome = "Oleo sintetico 5W30",
        unidadeDeMedida = "LITRO",
        custo = BigDecimal("38.90"),
    )

    private fun saldo() = SaldoDeInsumoResponse(
        insumoId = "018f30bb-77a1-7c22-9b10-2a44de81f0aa",
        sku = "OL-5W30-SYN-1L",
        unidadeDeMedida = "LITRO",
        quantidadeDisponivel = BigDecimal("48"),
        quantidadeReservada = BigDecimal("4"),
    )

    @Test
    fun `mapeia servico e insumo para o dominio`() {
        StepVerifier.create(adaptador().listarServicos())
            .assertNext { servico ->
                assertEquals("Troca de oleo e filtro", servico.nome)
                assertEquals(0, servico.valorReferencia!!.compareTo(BigDecimal("189.90")))
            }
            .verifyComplete()

        StepVerifier.create(adaptador().listarInsumos())
            .assertNext { insumo ->
                assertEquals("OL-5W30-SYN-1L", insumo.sku)
                assertEquals("LITRO", insumo.unidadeDeMedida)
            }
            .verifyComplete()
    }

    @Test
    fun `item inativo do catalogo nao chega ao cliente`() {
        val adaptador = adaptador(
            servicos = { Flux.just(servico(), servico().copy(id = "outro", ativo = false)) },
            insumos = { Flux.just(insumo().copy(ativo = false)) },
        )

        StepVerifier.create(adaptador.listarServicos()).expectNextCount(1).verifyComplete()
        StepVerifier.create(adaptador.listarInsumos()).verifyComplete()
    }

    @Test
    fun `mapeia o saldo, que e o que diz se da para reservar`() {
        StepVerifier.create(adaptador().saldoDe("018f30bb-77a1-7c22-9b10-2a44de81f0aa"))
            .assertNext { saldo ->
                assertEquals(0, saldo.quantidadeDisponivel.compareTo(BigDecimal("48")))
                assertEquals(0, saldo.quantidadeReservada.compareTo(BigDecimal("4")))
            }
            .verifyComplete()
    }

    @Test
    fun `busca em lote devolve mapa por identificador`() {
        StepVerifier.create(adaptador().buscarServicos(setOf("a", "b")))
            .assertNext { mapa -> assertEquals(setOf("a", "b"), mapa.keys) }
            .verifyComplete()

        StepVerifier.create(adaptador().buscarInsumos(setOf("a")))
            .assertNext { mapa -> assertEquals(setOf("a"), mapa.keys) }
            .verifyComplete()
    }

    @Test
    fun `busca em lote sem identificador nao chama o servico`() {
        chamadas.set(0)

        StepVerifier.create(adaptador().buscarServicos(emptySet()))
            .assertNext { mapa -> assertEquals(emptyMap(), mapa) }
            .verifyComplete()

        assertEquals(0, chamadas.get(), "lote vazio nao pode gerar chamada de rede")
    }

    @Test
    fun `listagem tolera 404 devolvendo vazio, em vez de estourar`() {
        val adaptador = adaptador(servicos = { Flux.error(erroHttp(HttpStatus.NOT_FOUND)) })

        StepVerifier.create(adaptador.listarServicos()).verifyComplete()
    }

    @Test
    fun `busca pontual propaga recurso nao encontrado`() {
        val adaptador = adaptador(oSaldo = { Mono.error(erroHttp(HttpStatus.NOT_FOUND)) })

        StepVerifier.create(adaptador.saldoDe("inexistente"))
            .expectError(RecursoNaoEncontradoException::class.java)
            .verify()
    }

    @Test
    fun `credencial recusada vira nao autorizado, e nao e retentada`() {
        chamadas.set(0)
        val adaptador = adaptador(oSaldo = { Mono.error(erroHttp(HttpStatus.UNAUTHORIZED)) })

        StepVerifier.create(adaptador.saldoDe("qualquer"))
            .expectError(NaoAutorizadoException::class.java)
            .verify()

        assertEquals(1, chamadas.get(), "credencial recusada nao melhora com retentativa")
    }

    @Test
    fun `erro de servidor vira indisponibilidade e e retentado ate o limite`() {
        chamadas.set(0)
        val adaptador = adaptador(oSaldo = { Mono.error(erroHttp(HttpStatus.SERVICE_UNAVAILABLE)) })

        StepVerifier.create(adaptador.saldoDe("qualquer"))
            .expectError(ServicoIndisponivelException::class.java)
            .verify()

        assertEquals(3, chamadas.get())
    }

    @Test
    fun `requisicao invalida do cliente sobe como veio, sem virar indisponibilidade`() {
        chamadas.set(0)
        val adaptador = adaptador(oSaldo = { Mono.error(erroHttp(HttpStatus.BAD_REQUEST)) })

        StepVerifier.create(adaptador.saldoDe("qualquer"))
            .expectError(WebClientResponseException::class.java)
            .verify()

        assertEquals(1, chamadas.get(), "erro de 4xx do proprio BFF nao se conserta retentando")
    }
}
