package com.clau.service_track.bff.infra.client.usuarios

import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class UsuariosWebClientTest {

    private var enviado: ClientRequest? = null

    private fun clienteQueResponde(corpo: String) = UsuariosWebClient(
        WebClient.builder()
            .baseUrl("http://usuarios.test")
            .exchangeFunction { requisicao ->
                enviado = requisicao
                Mono.just(
                    ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .body(corpo)
                        .build(),
                )
            }
            .build(),
    )

    private val cliente = clienteQueResponde("[]")

    private val umUsuario = """
        {"id":"abc","documento":"52998224725","nome":"Joana","email":null,"tipoDeUsuario":"CLIENTE"}
    """.trimIndent()

    @Test
    fun `filtra usuarios pelo parametro tipo, que e o nome que o servico declara`() {
        StepVerifier.create(cliente.listarPorTipo("MECANICO")).verifyComplete()

        assertEquals("/usuarios?tipo=MECANICO", enviado?.url()?.let { "${it.path}?${it.query}" })
    }

    @Test
    fun `filtra veiculos por clienteId`() {
        StepVerifier.create(cliente.listarVeiculosDoCliente("abc")).verifyComplete()

        assertEquals("/veiculos?clienteId=abc", enviado?.url()?.let { "${it.path}?${it.query}" })
    }

    @Test
    fun `busca usuario por identificador no caminho`() {
        StepVerifier.create(clienteQueResponde(umUsuario).buscarPorId("abc"))
            .expectNextCount(1)
            .verifyComplete()

        assertEquals("/usuarios/abc", enviado?.url()?.path)
    }
}
