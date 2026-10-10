package com.clau.service_track.bff.domain.model

import java.math.BigDecimal

data class PassoDaSaga(
    val insumoId: String,
    val etapa: String,
    val quantidade: BigDecimal,
    val situacao: String,
    val motivo: String?,
)
