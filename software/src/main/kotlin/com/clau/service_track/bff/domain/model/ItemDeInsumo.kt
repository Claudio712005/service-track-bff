package com.clau.service_track.bff.domain.model

import java.math.BigDecimal

data class ItemDeInsumo(
    val id: String,
    val insumoId: String,
    val insumo: Insumo?,
    val quantidade: BigDecimal,
)
