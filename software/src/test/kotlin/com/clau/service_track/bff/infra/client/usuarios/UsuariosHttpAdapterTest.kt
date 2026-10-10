package com.clau.service_track.bff.infra.client.usuarios

import com.clau.service_track.bff.domain.exception.RecursoNaoEncontradoException
import com.clau.service_track.bff.domain.exception.ServicoIndisponivelException
import com.clau.service_track.bff.domain.model.TipoDeUsuario
import com.clau.service_track.bff.infra.config.FabricaDeResiliencia
import com.clau.service_track.bff.infra.config.ResilienceProperties
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

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

    private fun erroHttp(status: HttpStatus) = WebClientResponseException(
        status.value(),
        status.reasonPhrase,
        null,
        null,
        null,
    )

    private fun adaptador(
        usuarios: () -> Flux<UsuarioResponse> = { Flux.just(usuario()) },
        porId: () -> Mono<UsuarioResponse> = { Mono.just(usuario()) },
        veiculos: () -> Flux<VeiculoResponse> = { Flux.empty() },
    ): UsuariosHttpAdapter {
        val cliente = object : UsuariosApiClient {
            override fun listarPorTipo(tipo: String): Flux<UsuarioResponse> {
                chamadas.incrementAndGet()
                return usuarios()
            }

            override fun buscarPorId(id: String): Mono<UsuarioResponse> {
                chamadas.incrementAndGet()
                return porId()
            }

            override fun listarVeiculosDoCliente(clienteId: String): Flux<VeiculoResponse> {
                chamadas.incrementAndGet()
                return veiculos()
            }
        }

        val fabrica = FabricaDeResiliencia(properties)
        return UsuariosHttpAdapter(
            cliente = cliente,
            retryDeUsuarios = fabrica.retry("usuarios"),
            disjuntorDeUsuarios = fabrica.circuitBreaker("usuarios"),
        )
    }

    private fun usuario() = UsuarioResponse(
        id = "018f2c9a-5f2e-7c31-9a41-6f3b2d0e9c11",
        documento = "52998224725",
        nome = "Joana",
        email = "joana@exemplo.test",
        tipoDeUsuario = "CLIENTE",
    )

    @Test
    fun `mapeia a resposta do servico para o dominio`() {
        StepVerifier.create(adaptador().listarPorTipo(TipoDeUsuario.CLIENTE))
            .assertNext { pessoa ->
                assertEquals("Joana", pessoa.nome)
                assertEquals(TipoDeUsuario.CLIENTE, pessoa.tipoDeUsuario)
            }
            .verifyComplete()

        assertEquals(1, chamadas.get())
    }

    @Test
    fun `retenta ate o limite quando o servico responde 503`() {
        val adaptador = adaptador(usuarios = { Flux.error(erroHttp(HttpStatus.SERVICE_UNAVAILABLE)) })

        StepVerifier.create(adaptador.listarPorTipo(TipoDeUsuario.CLIENTE))
            .expectError(ServicoIndisponivelException::class.java)
            .verify()

        assertEquals(3, chamadas.get())
    }

    @Test
    fun `nao retenta quando o recurso nao existe`() {
        val adaptador = adaptador(porId = { Mono.error(erroHttp(HttpStatus.NOT_FOUND)) })

        StepVerifier.create(adaptador.buscarPorId("018f2c9a-5f2e-7c31-9a41-6f3b2d0e9c11"))
            .expectError(RecursoNaoEncontradoException::class.java)
            .verify()

        assertEquals(1, chamadas.get())
    }

    @Test
    fun `aproveita o sucesso da segunda tentativa`() {
        val adaptador = adaptador(
            usuarios = {
                if (chamadas.get() == 1) {
                    Flux.error(erroHttp(HttpStatus.BAD_GATEWAY))
                } else {
                    Flux.just(usuario())
                }
            },
        )

        StepVerifier.create(adaptador.listarPorTipo(TipoDeUsuario.CLIENTE))
            .expectNextCount(1)
            .verifyComplete()

        assertEquals(2, chamadas.get())
    }
}
