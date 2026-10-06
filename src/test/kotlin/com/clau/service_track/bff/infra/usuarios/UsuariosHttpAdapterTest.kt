package com.clau.service_track.bff.infra.usuarios

import com.clau.service_track.bff.domain.TipoDeUsuario
import com.clau.service_track.bff.domain.exception.RecursoNaoEncontradoException
import com.clau.service_track.bff.domain.exception.ServicoIndisponivelException
import com.clau.service_track.bff.infra.config.ResilienceConfig
import com.clau.service_track.bff.infra.config.ResilienceProperties
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import reactor.test.StepVerifier
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

class UsuariosHttpAdapterTest {

    private val chamadas = AtomicInteger()

    private val properties = ResilienceProperties(
        maxAttempts = 3,
        initialBackoff = Duration.ofMillis(1),
        backoffMultiplier = 2.0,
        maxBackoff = Duration.ofMillis(5),
        slidingWindowSize = 100,
        minimumNumberOfCalls = 100,
    )

    private fun adaptador(resposta: () -> ClientResponse): UsuariosHttpAdapter {
        val cliente = WebClient.builder()
            .baseUrl("http://usuarios.test")
            .exchangeFunction {
                chamadas.incrementAndGet()
                Mono.just(resposta())
            }
            .build()

        val config = ResilienceConfig()
        return UsuariosHttpAdapter(
            webClientDeUsuarios = cliente,
            retryDeUsuarios = config.retryDeUsuarios(properties),
            disjuntorDeUsuarios = config.disjuntorDeUsuarios(properties),
        )
    }

    private fun json(corpo: String, status: HttpStatus = HttpStatus.OK) = ClientResponse
        .create(status)
        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        .body(corpo)
        .build()

    @Test
    fun `mapeia a lista de usuarios para o dominio`() {
        val adaptador = adaptador {
            json(
                """
                [{"id":"1","documento":"52998224725","nome":"Joana","email":"j@x.test","tipoDeUsuario":"CLIENTE"}]
                """.trimIndent(),
            )
        }

        StepVerifier.create(adaptador.listarPorTipo(TipoDeUsuario.CLIENTE))
            .assertNext { pessoa ->
                assertEquals("Joana", pessoa.nome)
                assertEquals(TipoDeUsuario.CLIENTE, pessoa.tipoDeUsuario)
            }
            .verifyComplete()

        assertEquals(1, chamadas.get())
    }

    @Test
    fun `retenta ate o limite quando o servico responde 503`() {
        val adaptador = adaptador { json("{}", HttpStatus.SERVICE_UNAVAILABLE) }

        StepVerifier.create(adaptador.listarPorTipo(TipoDeUsuario.CLIENTE))
            .expectError(ServicoIndisponivelException::class.java)
            .verify()

        assertEquals(3, chamadas.get())
    }

    @Test
    fun `nao retenta quando o recurso nao existe`() {
        val adaptador = adaptador { json("{}", HttpStatus.NOT_FOUND) }

        StepVerifier.create(adaptador.buscarPorId("018f2c9a-5f2e-7c31-9a41-6f3b2d0e9c11"))
            .expectError(RecursoNaoEncontradoException::class.java)
            .verify()

        assertEquals(1, chamadas.get())
    }

    @Test
    fun `aproveita o sucesso da segunda tentativa`() {
        val adaptador = adaptador {
            if (chamadas.get() == 1) {
                json("{}", HttpStatus.SERVICE_UNAVAILABLE)
            } else {
                json(
                    """
                    [{"id":"1","documento":"52998224725","nome":"Joana","email":null,"tipoDeUsuario":"MECANICO"}]
                    """.trimIndent(),
                )
            }
        }

        StepVerifier.create(adaptador.listarPorTipo(TipoDeUsuario.MECANICO))
            .expectNextCount(1)
            .verifyComplete()

        assertEquals(2, chamadas.get())
    }
}
