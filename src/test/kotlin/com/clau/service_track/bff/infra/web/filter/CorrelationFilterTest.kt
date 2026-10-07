package com.clau.service_track.bff.infra.web.filter

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import com.clau.service_track.bff.infra.client.usuarios.UsuariosHttpAdapter
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.web.reactive.server.WebTestClient

@org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing
@SpringBootTest(
    properties = [
        "servicetrack.usuarios.base-url=http://localhost:9999",
        "ST_BFF_LOG_FORMAT=",
    ],
)
class CorrelationFilterTest {

    @Autowired
    private lateinit var contexto: ApplicationContext

    private val cliente: WebTestClient by lazy {
        WebTestClient.bindToApplicationContext(contexto).build()
    }

    private val coletor = ListAppender<ILoggingEvent>()

    private val loggers = listOf(
        LoggerFactory.getLogger(CorrelationFilter::class.java) as Logger,
        LoggerFactory.getLogger(UsuariosHttpAdapter::class.java) as Logger,
    )

    @BeforeTest
    fun ligarColetor() {
        coletor.start()
        loggers.forEach { it.addAppender(coletor) }
    }

    @AfterTest
    fun desligarColetor() {
        loggers.forEach { it.detachAppender(coletor) }
        coletor.stop()
    }

    @Test
    fun `devolve a correlacao recebida e a publica no MDC do log de acesso`() {
        val correlacao = "correlacao-de-teste-1"

        val resposta = cliente.get().uri("/clientes")
            .header(CorrelationFilter.CORRELATION_HEADER, correlacao)
            .exchange()
            .returnResult(String::class.java)

        assertEquals(correlacao, resposta.responseHeaders.getFirst(CorrelationFilter.CORRELATION_HEADER))
        assertNotNull(resposta.responseHeaders.getFirst(CorrelationFilter.REQUEST_HEADER))

        val linha = coletor.list.firstOrNull { it.message.startsWith("requisicao concluida") }
        assertNotNull(linha, "nenhuma linha de acesso foi registrada")

        assertEquals(correlacao, linha.mdcPropertyMap[CorrelationFilter.CORRELATION_FIELD])
        assertNotNull(linha.mdcPropertyMap[CorrelationFilter.REQUEST_FIELD])
        assertTrue(linha.formattedMessage.contains("rota=/clientes"))
    }

    @Test
    fun `gera correlacao quando o cliente nao manda`() {
        val resposta = cliente.get().uri("/mecanicos").exchange().returnResult(String::class.java)

        val gerada = resposta.responseHeaders.getFirst(CorrelationFilter.CORRELATION_HEADER)
        assertNotNull(gerada)
        assertEquals(36, gerada.length)
    }

    @Test
    fun `a correlacao sobrevive a troca de thread ate o adaptador`() {
        val correlacao = "correlacao-entre-threads"

        cliente.get().uri("/mecanicos")
            .header(CorrelationFilter.CORRELATION_HEADER, correlacao)
            .exchange()
            .returnResult(String::class.java)

        val doAdaptador = coletor.list.firstOrNull { it.loggerName.endsWith("UsuariosHttpAdapter") }
        assertNotNull(doAdaptador, "o adaptador nao registrou nada; o teste nao provou nada")

        assertEquals(
            correlacao,
            doAdaptador.mdcPropertyMap[CorrelationFilter.CORRELATION_FIELD],
            "contexto perdido entre a thread do servidor e a thread do cliente HTTP",
        )

        val doFiltro = coletor.list.firstOrNull { it.message.startsWith("requisicao concluida") }
        assertNotNull(doFiltro)
        assertEquals(
            doAdaptador.mdcPropertyMap[CorrelationFilter.REQUEST_FIELD],
            doFiltro.mdcPropertyMap[CorrelationFilter.REQUEST_FIELD],
            "filtro e adaptador viram requestId diferentes na mesma requisicao",
        )
    }

    @Test
    fun `a thread do adaptador e diferente da thread do filtro`() {
        cliente.get().uri("/mecanicos").exchange().returnResult(String::class.java)

        val threads = coletor.list.map { it.threadName }.distinct()
        assertTrue(
            threads.size > 1,
            "tudo rodou na mesma thread ($threads): o teste de propagacao nao exercitou troca de thread",
        )
    }

    @Test
    fun `o corpo de erro carrega a correlacao lida do contexto`() {
        val correlacao = "correlacao-no-corpo-de-erro"

        cliente.get().uri("/clientes")
            .header(CorrelationFilter.CORRELATION_HEADER, correlacao)
            .exchange()
            .expectStatus().is5xxServerError
            .expectBody()
            .jsonPath("$.correlationId").isEqualTo(correlacao)
            .jsonPath("$.requestId").exists()
            .jsonPath("$.rota").isEqualTo("/clientes")
    }
}
