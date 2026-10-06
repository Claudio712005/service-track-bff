package com.clau.service_track.bff.dominio.porta

import com.clau.service_track.bff.dominio.Pessoa
import com.clau.service_track.bff.dominio.TipoDeUsuario
import com.clau.service_track.bff.dominio.Veiculo

interface UsuariosPort {

    suspend fun listarPorTipo(tipo: TipoDeUsuario): List<Pessoa>

    suspend fun buscarPorId(id: String): Pessoa

    suspend fun listarVeiculosDoCliente(clienteId: String): List<Veiculo>
}
