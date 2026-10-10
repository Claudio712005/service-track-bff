package com.clau.service_track.bff.infra.client.ordens

data class PaginaDeOrdensResponse(
    val conteudo: List<OrdemServicoResponse> = emptyList(),
    val total: Long = 0,
)
