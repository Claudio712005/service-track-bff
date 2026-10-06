package com.clau.service_track.bff.infra.client.usuarios

data class UsuarioResponse(
    val id: String,
    val documento: String,
    val nome: String,
    val email: String?,
    val tipoDeUsuario: String,
)
