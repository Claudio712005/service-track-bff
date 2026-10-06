package com.clau.service_track.bff.application

import com.clau.service_track.bff.domain.Pessoa
import com.clau.service_track.bff.domain.TipoDeUsuario
import com.clau.service_track.bff.domain.Veiculo
import com.clau.service_track.bff.domain.exception.RecursoNaoEncontradoException
import com.clau.service_track.bff.domain.exception.ServicoIndisponivelException
import com.clau.service_track.bff.domain.port.UsuariosPort
import kotlin.test.Test
import kotlin.test.assertEquals
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class ClientesServiceTest {

    private fun pessoa(tipo: TipoDeUsuario = TipoDeUsuario.CLIENTE) = Pessoa(
        id = ID,
        documento = "52998224725",
        nome = "Joana Ferreira",
        email = "joana@exemplo.test",
        tipoDeUsuario = tipo,
    )

    private val veiculo = Veiculo(
        id = "018f2c9a-5f2e-7c31-9a41-6f3b2d0e9c22",
        placa = "ABC1D23",
        marca = "Fiat",
        modelo = "Argo",
        anoModelo = 2023,
    )

    private fun porta(
        listar: () -> Flux<Pessoa> = { Flux.just(pessoa()) },
        buscar: () -> Mono<Pessoa> = { Mono.just(pessoa()) },
        veiculos: () -> Flux<Veiculo> = { Flux.just(veiculo) },
    ) = object : UsuariosPort {
        override fun listarPorTipo(tipo: TipoDeUsuario) = listar()
        override fun buscarPorId(id: String) = buscar()
        override fun listarVeiculosDoCliente(clienteId: String) = veiculos()
    }

    @Test
    fun `lista clientes pedindo o tipo certo ao servico`() {
        var tipoPedido: TipoDeUsuario? = null
        val espiao = object : UsuariosPort {
            override fun listarPorTipo(tipo: TipoDeUsuario): Flux<Pessoa> {
                tipoPedido = tipo
                return Flux.just(pessoa())
            }

            override fun buscarPorId(id: String) = Mono.just(pessoa())
            override fun listarVeiculosDoCliente(clienteId: String) = Flux.empty<Veiculo>()
        }

        StepVerifier.create(ClientesService(espiao).listar())
            .expectNextCount(1)
            .verifyComplete()

        assertEquals(TipoDeUsuario.CLIENTE, tipoPedido)
    }

    @Test
    fun `compoe cliente com os veiculos dele`() {
        StepVerifier.create(ClientesService(porta()).comVeiculos(ID))
            .assertNext { resultado ->
                assertEquals("Joana Ferreira", resultado.cliente.nome)
                assertEquals(listOf(veiculo), resultado.veiculos)
                assertEquals(false, resultado.veiculosIndisponiveis)
            }
            .verifyComplete()
    }

    @Test
    fun `devolve o cliente sem veiculos quando a chamada de veiculos falha`() {
        val porta = porta(veiculos = { Flux.error(ServicoIndisponivelException("usuarios")) })

        StepVerifier.create(ClientesService(porta).comVeiculos(ID))
            .assertNext { resultado ->
                assertEquals("Joana Ferreira", resultado.cliente.nome)
                assertEquals(emptyList(), resultado.veiculos)
                assertEquals(true, resultado.veiculosIndisponiveis)
            }
            .verifyComplete()
    }

    @Test
    fun `nao devolve mecanico na rota de cliente`() {
        val porta = porta(buscar = { Mono.just(pessoa(TipoDeUsuario.MECANICO)) })

        StepVerifier.create(ClientesService(porta).comVeiculos(ID))
            .expectError(RecursoNaoEncontradoException::class.java)
            .verify()
    }

    @Test
    fun `cliente ausente vira recurso nao encontrado`() {
        val porta = porta(buscar = { Mono.empty() })

        StepVerifier.create(ClientesService(porta).comVeiculos(ID))
            .expectError(RecursoNaoEncontradoException::class.java)
            .verify()
    }

    @Test
    fun `indisponibilidade do cliente nao e mascarada`() {
        val porta = porta(buscar = { Mono.error(ServicoIndisponivelException("usuarios")) })

        StepVerifier.create(ClientesService(porta).comVeiculos(ID))
            .expectError(ServicoIndisponivelException::class.java)
            .verify()
    }

    private companion object {
        const val ID = "018f2c9a-5f2e-7c31-9a41-6f3b2d0e9c11"
    }
}
