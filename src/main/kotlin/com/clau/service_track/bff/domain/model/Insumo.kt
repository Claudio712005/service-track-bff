package com.clau.service_track.bff.domain.model

import java.math.BigDecimal

data class Insumo(
    val id: String,
    val sku: String,
    val nome: String,
    val unidadeDeMedida: String,
    val custo: BigDecimal?,
)
