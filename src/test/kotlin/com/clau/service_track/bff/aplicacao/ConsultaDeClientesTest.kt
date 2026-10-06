package com.clau.service_track.bff.aplicacao

import com.clau.service_track.bff.dominio.Pessoa
import com.clau.service_track.bff.dominio.TipoDeUsuario
import com.clau.service_track.bff.dominio.Veiculo
import com.clau.service_track.bff.dominio.excecao.RecursoNaoEncontradoException
import com.clau.service_track.bff.dominio.excecao.ServicoIndisponivelException
import com.clau.service_track.bff.dominio.porta.UsuariosPort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ConsultaDeClientesTest {

    private fun cliente(tipo: TipoDeUsuario = TipoDeUsuario.CLIENTE) = Pessoa(
        id = "018f2c9a-5f2e-7c31-9a41-6f3b2d0e9c11",
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
        listar: () -> List<Pessoa> = { listOf(cliente()) },
        buscar: () -> Pessoa = { cliente() },
        veiculos: () -> List<Veiculo> = { listOf(veiculo) },
    ) = object : UsuariosPort {
        override suspend fun listarPorTipo(tipo: TipoDeUsuario) = listar()
        override suspend fun buscarPorId(id: String) = buscar()
        override suspend fun listarVeiculosDoCliente(clienteId: String) = veiculos()
    }

    @Test
    fun `lista clientes pedindo o tipo certo ao servico`() = runTest {
        var tipoPedido: TipoDeUsuario? = null
        val espiao = object : UsuariosPort {
            override suspend fun listarPorTipo(tipo: TipoDeUsuario): List<Pessoa> {
                tipoPedido = tipo
                return listOf(cliente())
            }

            override suspend fun buscarPorId(id: String) = cliente()
            override suspend fun listarVeiculosDoCliente(clienteId: String) = emptyList<Veiculo>()
        }

        val clientes = ConsultaDeClientes(espiao).listar()

        assertEquals(TipoDeUsuario.CLIENTE, tipoPedido)
        assertEquals(1, clientes.size)
    }

    @Test
    fun `compoe cliente com os veiculos dele`() = runTest {
        val resultado = ConsultaDeClientes(porta()).comVeiculos("018f2c9a-5f2e-7c31-9a41-6f3b2d0e9c11")

        assertEquals("Joana Ferreira", resultado.cliente.nome)
        assertEquals(listOf(veiculo), resultado.veiculos)
        assertEquals(false, resultado.veiculosIndisponiveis)
    }

    @Test
    fun `devolve o cliente sem veiculos quando a chamada de veiculos falha`() = runTest {
        val porta = porta(veiculos = { throw ServicoIndisponivelException("usuarios") })

        val resultado = ConsultaDeClientes(porta).comVeiculos("018f2c9a-5f2e-7c31-9a41-6f3b2d0e9c11")

        assertEquals("Joana Ferreira", resultado.cliente.nome)
        assertTrue(resultado.veiculos.isEmpty())
        assertTrue(resultado.veiculosIndisponiveis)
    }

    @Test
    fun `nao devolve mecanico na rota de cliente`() = runTest {
        val porta = porta(buscar = { cliente(TipoDeUsuario.MECANICO) })

        assertFailsWith<RecursoNaoEncontradoException> {
            ConsultaDeClientes(porta).comVeiculos("018f2c9a-5f2e-7c31-9a41-6f3b2d0e9c11")
        }
    }

    @Test
    fun `indisponibilidade do cliente nao e mascarada`() = runTest {
        val porta = porta(buscar = { throw ServicoIndisponivelException("usuarios") })

        assertFailsWith<ServicoIndisponivelException> {
            ConsultaDeClientes(porta).comVeiculos("018f2c9a-5f2e-7c31-9a41-6f3b2d0e9c11")
        }
    }
}
