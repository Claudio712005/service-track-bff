package com.clau.service_track.bff.aplicacao

import com.clau.service_track.bff.dominio.Pessoa
import com.clau.service_track.bff.dominio.TipoDeUsuario
import com.clau.service_track.bff.dominio.excecao.RecursoNaoEncontradoException
import com.clau.service_track.bff.dominio.porta.UsuariosPort
import org.springframework.stereotype.Service

@Service
class ConsultaDeMecanicos(
    private val usuarios: UsuariosPort,
) {

    suspend fun listar(): List<Pessoa> = usuarios.listarPorTipo(TipoDeUsuario.MECANICO)

    suspend fun buscar(id: String): Pessoa {
        val pessoa = usuarios.buscarPorId(id)

        if (pessoa.tipoDeUsuario != TipoDeUsuario.MECANICO) {
            throw RecursoNaoEncontradoException("Mecanico $id nao encontrado")
        }

        return pessoa
    }
}
