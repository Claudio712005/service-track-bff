package com.clau.service_track.bff.infra.client.ordens

import java.math.BigDecimal
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class OrdensWebClientTest {

    private val requisicoes = CopyOnWriteArrayList<ClientRequest>()

    private fun clienteQueResponde(corpo: String): OrdensWebClient {
        val troca = ExchangeFunction { requisicao ->
            requisicoes.add(requisicao)
            Mono.just(
                ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body(corpo)
                    .build()
            )
        }

        return OrdensWebClient(
            WebClient.builder().baseUrl("http://ordens.test").exchangeFunction(troca).build()
        )
    }

    private val umaOrdem = """
        {"id":"o-1","motivo":"barulho","clienteId":"c-1","mecanicoId":"m-1","veiculoId":"v-1",
         "status":"RECEBIDA","dataCriacao":"2026-10-08T09:00:00","dataAtualizacao":"2026-10-08T09:00:00"}
    """.trimIndent().replace("\n", "")

    @Test
    fun `abertura usa POST na colecao e leva o corpo`() {
        val cliente = clienteQueResponde(umaOrdem)
        val pedido = AbrirOrdemRequest(
            motivo = "barulho",
            clienteId = "c-1",
            mecanicoId = "m-1",
            veiculoId = "v-1",
        )

        StepVerifier.create(cliente.abrir(pedido)).expectNextCount(1).verifyComplete()

        assertEquals(HttpMethod.POST, requisicoes.last().method())
        assertEquals("http://ordens.test/ordens", requisicoes.last().url().toString())
    }

    @Test
    fun `consulta por identificador usa o caminho do recurso`() {
        StepVerifier.create(clienteQueResponde(umaOrdem).buscarPorId("o-1")).expectNextCount(1).verifyComplete()

        assertEquals("http://ordens.test/ordens/o-1", requisicoes.last().url().toString())
    }

    @Test
    fun `listagem do cliente vai por parametro de consulta, com tamanho de pagina`() {
        val pagina = """{"conteudo":[$umaOrdem],"total":1}"""

        StepVerifier.create(clienteQueResponde(pagina).listarDoCliente("c-1", 50))
            .assertNext { resposta -> assertEquals(1, resposta.conteudo.size) }
            .verifyComplete()

        val url = requisicoes.last().url().toString()
        assertTrue(url.contains("clienteId=c-1"), url)
        assertTrue(url.contains("tamanho=50"), url)
    }

    @Test
    fun `historico e saga ficam sob o recurso da ordem`() {
        val fato = """[{"statusNovo":"RECEBIDA","transicionou":true,"ocorridoEm":"2026-10-08T12:00:00Z"}]"""
        StepVerifier.create(clienteQueResponde(fato).historicoDe("o-1")).expectNextCount(1).verifyComplete()
        assertTrue(requisicoes.last().url().toString().endsWith("/ordens/o-1/historico"))

        val saga = """
            [{"tipo":"RESERVA","situacao":"EM_CURSO","etapa":"RESERVA_DE_INSUMOS",
              "passos":[{"insumoId":"i-1","etapa":"RESERVA_DE_INSUMOS","quantidade":2,"situacao":"PEDIDO"}]}]
        """.trimIndent().replace("\n", "")
        StepVerifier.create(clienteQueResponde(saga).sagasDe("o-1"))
            .assertNext { resposta -> assertEquals(0, resposta.passos.single().quantidade.compareTo(BigDecimal("2"))) }
            .verifyComplete()
        assertTrue(requisicoes.last().url().toString().endsWith("/ordens/o-1/saga"))
    }

    @Test
    fun `aprovacao do orcamento usa POST no subrecurso`() {
        StepVerifier.create(clienteQueResponde(umaOrdem).aprovarOrcamento("o-1")).expectNextCount(1).verifyComplete()

        assertEquals(HttpMethod.POST, requisicoes.last().method())
        assertTrue(requisicoes.last().url().toString().endsWith("/ordens/o-1/orcamento/aprovacao"))
    }
}
