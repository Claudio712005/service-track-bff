package com.clau.service_track.bff.domain.model

import java.math.BigDecimal

data class ItemDeServico(
    val id: String,
    val servicoId: String,
    val servico: Servico?,
    val valor: BigDecimal,
    val feito: Boolean,
)
