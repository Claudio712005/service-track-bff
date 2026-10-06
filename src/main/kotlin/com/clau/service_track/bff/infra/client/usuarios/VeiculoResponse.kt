package com.clau.service_track.bff.infra.client.usuarios

data class VeiculoResponse(
    val id: String,
    val clienteId: String,
    val placa: String,
    val marca: String,
    val modelo: String,
    val anoModelo: Int,
)
