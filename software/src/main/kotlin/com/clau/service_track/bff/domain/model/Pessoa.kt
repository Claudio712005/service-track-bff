package com.clau.service_track.bff.domain.model

data class Pessoa(
    val id: String,
    val documento: String,
    val nome: String,
    val email: String?,
    val tipoDeUsuario: TipoDeUsuario,
)
