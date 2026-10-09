package com.clau.service_track.bff.infra.web.error

data class ErrorResponse(
    val status: Int,
    val erro: String,
    val mensagem: String,
    val rota: String,
    val correlationId: String?,
    val requestId: String?,
)
