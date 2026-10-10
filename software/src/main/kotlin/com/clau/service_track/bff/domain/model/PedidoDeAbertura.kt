package com.clau.service_track.bff.domain.model

import java.time.LocalDateTime

data class PedidoDeAbertura(
    val motivo: String,
    val clienteId: String,
    val mecanicoId: String,
    val veiculoId: String,
    val observacao: String,
    val prazoConclusao: LocalDateTime?,
)
