package com.clau.service_track.bff.infra.client.ordens

import java.time.LocalDateTime

data class AbrirOrdemRequest(
    val motivo: String,
    val clienteId: String,
    val mecanicoId: String,
    val veiculoId: String,
    val observacao: String = "",
    val prazoConclusao: LocalDateTime? = null,
)
