package com.clau.service_track.bff.aplicacao

import com.clau.service_track.bff.dominio.ClienteComVeiculos
import com.clau.service_track.bff.dominio.Pessoa
import com.clau.service_track.bff.dominio.TipoDeUsuario
import com.clau.service_track.bff.dominio.excecao.RecursoNaoEncontradoException
import com.clau.service_track.bff.dominio.excecao.ServicoIndisponivelException
import com.clau.service_track.bff.dominio.porta.UsuariosPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ConsultaDeClientes(
    private val usuarios: UsuariosPort,
) {

    private val log = LoggerFactory.getLogger(ConsultaDeClientes::class.java)

    suspend fun listar(): List<Pessoa> = usuarios.listarPorTipo(TipoDeUsuario.CLIENTE)

    suspend fun comVeiculos(clienteId: String): ClienteComVeiculos {
        val cliente = usuarios.buscarPorId(clienteId)

        if (cliente.tipoDeUsuario != TipoDeUsuario.CLIENTE) {
            throw RecursoNaoEncontradoException("Cliente $clienteId nao encontrado")
        }

        return try {
            ClienteComVeiculos(
                cliente = cliente,
                veiculos = usuarios.listarVeiculosDoCliente(clienteId),
                veiculosIndisponiveis = false,
            )
        } catch (erro: ServicoIndisponivelException) {
            log.warn("cliente devolvido sem veiculos clienteId={} motivo={}", clienteId, erro.message)
            ClienteComVeiculos(cliente = cliente, veiculos = emptyList(), veiculosIndisponiveis = true)
        }
    }
}
