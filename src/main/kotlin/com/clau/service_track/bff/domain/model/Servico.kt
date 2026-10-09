package com.clau.service_track.bff.domain.model

import java.math.BigDecimal

data class Servico(
    val id: String,
    val nome: String,
    val valorReferencia: BigDecimal?,
)
