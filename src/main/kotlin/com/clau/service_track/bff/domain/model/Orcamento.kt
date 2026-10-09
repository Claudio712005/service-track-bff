package com.clau.service_track.bff.domain.model

import java.math.BigDecimal

data class Orcamento(
    val id: String,
    val custoMaoDeObra: BigDecimal,
    val custoInsumos: BigDecimal,
    val valorTotal: BigDecimal,
    val aprovado: Boolean,
)
