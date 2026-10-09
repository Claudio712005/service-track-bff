package com.clau.service_track.bff.infra.web.filter

import kotlin.test.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest
@ActiveProfiles("dev")
class PerfilDevTest {

    @Autowired
    private lateinit var contexto: ApplicationContext

    private val cliente: WebTestClient by lazy {
        WebTestClient.bindToApplicationContext(contexto).build()
    }

    @Test
    fun `o perfil dev responde sem nenhuma dependencia de rede`() {
        cliente.get().uri("/clientes")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(2)
            .jsonPath("$[0].tipoDeUsuario").isEqualTo("CLIENTE")
    }

    @Test
    fun `o cliente mocado vem com os veiculos dele`() {
        cliente.get().uri("/clientes/11111111-1111-4111-8111-111111111111")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.cliente.nome").isEqualTo("Joana Ferreira")
            .jsonPath("$.veiculos.length()").isEqualTo(2)
            .jsonPath("$.veiculosIndisponiveis").isEqualTo(false)
    }

    @Test
    fun `o mock tem um cliente que exercita a degradacao de veiculos`() {
        cliente.get().uri("/clientes/22222222-2222-4222-8222-222222222222")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.veiculos.length()").isEqualTo(0)
            .jsonPath("$.veiculosIndisponiveis").isEqualTo(true)
    }

    @Test
    fun `o catalogo mocado lista servico, insumo e saldo`() {
        cliente.get().uri("/catalogo/servicos")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].nome").isEqualTo("Troca de oleo e filtro")

        cliente.get().uri("/catalogo/insumos")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(2)

        cliente.get().uri("/catalogo/insumos/018f30bb-77a1-7c22-9b10-2a44de81f0aa/saldo")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.sku").isEqualTo("OL-5W30-SYN-1L")
            .jsonPath("$.quantidadeDisponivel").isEqualTo(48)
    }

    @Test
    fun `o mock tem um insumo de saldo zero, que e o gatilho de falha da saga`() {
        cliente.get().uri("/catalogo/insumos/018f30d0-9e11-7b44-9c55-3ad2f1b0e7c8/saldo")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.quantidadeDisponivel").isEqualTo(0)
    }

    @Test
    fun `saldo de insumo inexistente responde 404`() {
        cliente.get().uri("/catalogo/insumos/99999999-9999-4999-8999-999999999999/saldo")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `abre uma ordem e a detalha com os dados dos outros servicos resolvidos`() {
        val corpo = """
            {
              "motivo": "barulho na suspensao dianteira",
              "clienteId": "11111111-1111-4111-8111-111111111111",
              "mecanicoId": "33333333-3333-4333-8333-333333333333",
              "veiculoId": "aaaaaaaa-1111-4111-8111-111111111111",
              "observacao": "cliente aguarda na loja"
            }
        """.trimIndent()

        val id = cliente.post().uri("/ordens")
            .header("Content-Type", "application/json")
            .bodyValue(corpo)
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.status").isEqualTo("EM_DIAGNOSTICO")
            .returnResult()
            .responseBody!!
            .decodeToString()
            .substringAfter("\"id\":\"")
            .substringBefore("\"")

        cliente.get().uri("/ordens/$id")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.cliente.nome").isEqualTo("Joana Ferreira")
            .jsonPath("$.ordem.itensServico[0].servico.nome").isEqualTo("Troca de oleo e filtro")
            .jsonPath("$.ordem.itensInsumo[0].insumo.sku").isEqualTo("OL-5W30-SYN-1L")
            .jsonPath("$.parciaisIndisponiveis.length()").isEqualTo(0)

        cliente.get().uri("/ordens?clienteId=11111111-1111-4111-8111-111111111111")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)

        cliente.get().uri("/ordens/$id/saga")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(0)

        cliente.post().uri("/ordens/$id/orcamento/aprovacao")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.status").isEqualTo("AGUARDANDO_APROVACAO")
            .jsonPath("$.orcamento.aprovado").isEqualTo(true)

        cliente.get().uri("/ordens/$id/saga")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(1)
            .jsonPath("$[0].etapa").isEqualTo("RESERVA_DE_INSUMOS")

        cliente.get().uri("/ordens/$id/historico")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.length()").isEqualTo(2)
    }

    @Test
    fun `ordem inexistente responde 404 em vez de corpo vazio`() {
        cliente.get().uri("/ordens/99999999-9999-4999-8999-999999999999")
            .exchange()
            .expectStatus().isNotFound

        cliente.get().uri("/ordens/99999999-9999-4999-8999-999999999999/historico")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `identificador fora do formato UUID na abertura responde 400`() {
        val corpo = """
            {
              "motivo": "troca de oleo",
              "clienteId": "nao-e-uuid",
              "mecanicoId": "33333333-3333-4333-8333-333333333333",
              "veiculoId": "aaaaaaaa-1111-4111-8111-111111111111"
            }
        """.trimIndent()

        cliente.post().uri("/ordens")
            .header("Content-Type", "application/json")
            .bodyValue(corpo)
            .exchange()
            .expectStatus().isBadRequest
    }

    @Test
    fun `mecanico na rota de cliente responde 404`() {
        cliente.get().uri("/clientes/33333333-3333-4333-8333-333333333333")
            .exchange()
            .expectStatus().isNotFound
            .expectBody()
            .jsonPath("$.status").isEqualTo(404)
    }
}
