package com.clau.service_track.bff.infra.client.catalogo

import java.math.BigDecimal

data class InsumoResponse(
    val id: String,
    val sku: String,
    val nome: String,
    val unidadeDeMedida: String,
    val custo: BigDecimal? = null,
    val ativo: Boolean = true,
)
