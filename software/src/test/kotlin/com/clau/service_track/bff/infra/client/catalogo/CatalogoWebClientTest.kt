package com.clau.service_track.bff.infra.client.catalogo

import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class CatalogoWebClientTest {

    private val requisicoes = CopyOnWriteArrayList<ClientRequest>()

    private fun clienteQueResponde(corpo: String): CatalogoWebClient {
        val troca = ExchangeFunction { requisicao ->
            requisicoes.add(requisicao)
            Mono.just(
                ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body(corpo)
                    .build()
            )
        }

        return CatalogoWebClient(
            WebClient.builder().baseUrl("http://catalogo.test").exchangeFunction(troca).build()
        )
    }

    private val umServico = """[{"id":"s-1","nome":"Troca de oleo","valorReferencia":189.90,"ativo":true}]"""
    private val umInsumo = """[{"id":"i-1","sku":"OL-5W30","nome":"Oleo","unidadeDeMedida":"LITRO","custo":38.90,"ativo":true}]"""

    @Test
    fun `toda chamada declara a versao da api que o catalogo exige`() {
        val cliente = clienteQueResponde(umServico)

        StepVerifier.create(cliente.listarServicos()).expectNextCount(1).verifyComplete()

        assertEquals("1", requisicoes.single().headers().getFirst("X-API-Version"))
    }

    @Test
    fun `monta as rotas de listagem`() {
        StepVerifier.create(clienteQueResponde(umServico).listarServicos()).expectNextCount(1).verifyComplete()
        assertEquals("http://catalogo.test/servicos", requisicoes.last().url().toString())

        StepVerifier.create(clienteQueResponde(umInsumo).listarInsumos()).expectNextCount(1).verifyComplete()
        assertEquals("http://catalogo.test/insumos", requisicoes.last().url().toString())
    }

    @Test
    fun `monta as rotas pontuais com o identificador no caminho`() {
        val servico = """{"id":"s-1","nome":"Troca de oleo","valorReferencia":189.90,"ativo":true}"""
        StepVerifier.create(clienteQueResponde(servico).buscarServico("s-1")).expectNextCount(1).verifyComplete()
        assertEquals("http://catalogo.test/servicos/s-1", requisicoes.last().url().toString())

        val insumo = """{"id":"i-1","sku":"OL-5W30","nome":"Oleo","unidadeDeMedida":"LITRO","custo":38.90,"ativo":true}"""
        StepVerifier.create(clienteQueResponde(insumo).buscarInsumo("i-1")).expectNextCount(1).verifyComplete()
        assertEquals("http://catalogo.test/insumos/i-1", requisicoes.last().url().toString())
    }

    @Test
    fun `saldo fica sob a rota do insumo, que e onde o catalogo o expoe`() {
        val saldo = """
            {"insumoId":"i-1","sku":"OL-5W30","unidadeDeMedida":"LITRO",
             "quantidadeDisponivel":48,"quantidadeReservada":4}
        """.trimIndent()

        StepVerifier.create(clienteQueResponde(saldo).saldoDe("i-1"))
            .assertNext { resposta -> assertEquals("OL-5W30", resposta.sku) }
            .verifyComplete()

        assertTrue(requisicoes.last().url().toString().endsWith("/insumos/i-1/estoque"))
    }
}
