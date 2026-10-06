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
    fun `mecanico na rota de cliente responde 404`() {
        cliente.get().uri("/clientes/33333333-3333-4333-8333-333333333333")
            .exchange()
            .expectStatus().isNotFound
            .expectBody()
            .jsonPath("$.status").isEqualTo(404)
    }
}
