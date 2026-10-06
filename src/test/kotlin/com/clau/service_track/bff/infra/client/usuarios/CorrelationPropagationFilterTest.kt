package com.clau.service_track.bff.infra.client.usuarios

import com.clau.service_track.bff.infra.web.filter.CorrelationFilter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class CorrelationPropagationFilterTest {

    private var enviado: ClientRequest? = null

    private val cliente = WebClient.builder()
        .baseUrl("http://usuarios.test")
        .filter(CorrelationPropagationFilter())
        .exchangeFunction { requisicao ->
            enviado = requisicao
            Mono.just(ClientResponse.create(HttpStatus.OK).body("{}").build())
        }
        .build()

    private fun chamar() = cliente.get().uri("/usuarios").retrieve().bodyToMono(String::class.java)

    @Test
    fun `leva o cabecalho de correlacao lido do contexto para o servico de destino`() {
        val correlacao = "correlacao-para-fora"

        StepVerifier.create(
            chamar().contextWrite { contexto -> contexto.put(CorrelationFilter.CORRELATION_FIELD, correlacao) },
        ).expectNextCount(1).verifyComplete()

        assertEquals(correlacao, enviado?.headers()?.getFirst(CorrelationFilter.CORRELATION_HEADER))
    }

    @Test
    fun `nao inventa cabecalho quando o contexto nao tem correlacao`() {
        StepVerifier.create(chamar()).expectNextCount(1).verifyComplete()

        assertNull(enviado?.headers()?.getFirst(CorrelationFilter.CORRELATION_HEADER))
    }
}
