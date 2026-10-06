package com.clau.service_track.bff.infraestrutura.usuarios

import com.clau.service_track.bff.dominio.TipoDeUsuario
import com.clau.service_track.bff.dominio.excecao.RecursoNaoEncontradoException
import com.clau.service_track.bff.dominio.excecao.ServicoIndisponivelException
import com.clau.service_track.bff.infraestrutura.config.ResilienciaConfig
import com.clau.service_track.bff.infraestrutura.config.ResilienciaProperties
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

class UsuariosHttpAdapterTest {

    private val chamadas = AtomicInteger()

    private val propriedades = ResilienciaProperties(
        tentativas = 3,
        esperaInicial = Duration.ofMillis(1),
        multiplicadorDaEspera = 2.0,
        esperaMaxima = Duration.ofMillis(5),
        janelaDoDisjuntor = 100,
        chamadasMinimas = 100,
    )

    private fun adaptador(resposta: () -> ClientResponse): UsuariosHttpAdapter {
        val cliente = WebClient.builder()
            .baseUrl("http://usuarios.test")
            .exchangeFunction {
                chamadas.incrementAndGet()
                Mono.just(resposta())
            }
            .build()

        val config = ResilienciaConfig()
        return UsuariosHttpAdapter(
            webClientDeUsuarios = cliente,
            retryDeUsuarios = config.retryDeUsuarios(propriedades),
            disjuntorDeUsuarios = config.disjuntorDeUsuarios(propriedades),
        )
    }

    private fun json(corpo: String, status: HttpStatus = HttpStatus.OK) = ClientResponse
        .create(status)
        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        .body(corpo)
        .build()

    @Test
    fun `mapeia a lista de usuarios para o dominio`() = runTest {
        val adaptador = adaptador {
            json(
                """
                [{"id":"1","documento":"52998224725","nome":"Joana","email":"j@x.test","tipoDeUsuario":"CLIENTE"}]
                """.trimIndent(),
            )
        }

        val pessoas = adaptador.listarPorTipo(TipoDeUsuario.CLIENTE)

        assertEquals(1, pessoas.size)
        assertEquals("Joana", pessoas.first().nome)
        assertEquals(TipoDeUsuario.CLIENTE, pessoas.first().tipoDeUsuario)
        assertEquals(1, chamadas.get())
    }

    @Test
    fun `retenta ate o limite quando o servico responde 503`() = runTest {
        val adaptador = adaptador { json("{}", HttpStatus.SERVICE_UNAVAILABLE) }

        assertFailsWith<ServicoIndisponivelException> {
            adaptador.listarPorTipo(TipoDeUsuario.CLIENTE)
        }

        assertEquals(3, chamadas.get())
    }

    @Test
    fun `nao retenta quando o recurso nao existe`() = runTest {
        val adaptador = adaptador { json("{}", HttpStatus.NOT_FOUND) }

        assertFailsWith<RecursoNaoEncontradoException> {
            adaptador.buscarPorId("018f2c9a-5f2e-7c31-9a41-6f3b2d0e9c11")
        }

        assertEquals(1, chamadas.get())
    }

    @Test
    fun `desiste depois de uma falha seguida de sucesso tardio`() = runTest {
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

        val pessoas = adaptador.listarPorTipo(TipoDeUsuario.MECANICO)

        assertEquals(1, pessoas.size)
        assertEquals(2, chamadas.get())
    }
}
