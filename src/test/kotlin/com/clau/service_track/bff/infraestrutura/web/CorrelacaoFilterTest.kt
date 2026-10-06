package com.clau.service_track.bff.infraestrutura.web

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest
class CorrelacaoFilterTest {

    @Autowired
    private lateinit var contexto: ApplicationContext

    private val cliente: WebTestClient by lazy {
        WebTestClient.bindToApplicationContext(contexto).build()
    }

    private val coletor = ListAppender<ILoggingEvent>()
    private val logger = LoggerFactory.getLogger(CorrelacaoFilter::class.java) as Logger

    @BeforeTest
    fun ligarColetor() {
        coletor.start()
        logger.addAppender(coletor)
    }

    @AfterTest
    fun desligarColetor() {
        logger.detachAppender(coletor)
        coletor.stop()
    }

    @Test
    fun `devolve a correlacao recebida e a publica no MDC do log de acesso`() {
        val correlacao = "correlacao-de-teste-1"

        val resposta = cliente.get().uri("/clientes")
            .header(CorrelacaoFilter.CABECALHO_CORRELACAO, correlacao)
            .exchange()
            .returnResult(String::class.java)

        assertEquals(correlacao, resposta.responseHeaders.getFirst(CorrelacaoFilter.CABECALHO_CORRELACAO))
        assertNotNull(resposta.responseHeaders.getFirst(CorrelacaoFilter.CABECALHO_REQUISICAO))

        val linha = coletor.list.firstOrNull { it.message.startsWith("requisicao concluida") }
        assertNotNull(linha, "nenhuma linha de acesso foi registrada")

        assertEquals(correlacao, linha.mdcPropertyMap[CorrelacaoFilter.CHAVE_CORRELACAO])
        assertNotNull(linha.mdcPropertyMap[CorrelacaoFilter.CHAVE_REQUISICAO])
        assertTrue(linha.formattedMessage.contains("rota=/clientes"))
    }

    @Test
    fun `gera correlacao quando o cliente nao manda`() {
        val resposta = cliente.get().uri("/mecanicos").exchange().returnResult(String::class.java)

        val gerada = resposta.responseHeaders.getFirst(CorrelacaoFilter.CABECALHO_CORRELACAO)
        assertNotNull(gerada)
        assertEquals(36, gerada.length)
    }
}
