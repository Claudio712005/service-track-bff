package com.clau.service_track.bff.infraestrutura.web

data class ErroResponse(
    val status: Int,
    val erro: String,
    val mensagem: String,
    val rota: String,
    val correlationId: String?,
    val requestId: String?,
)
