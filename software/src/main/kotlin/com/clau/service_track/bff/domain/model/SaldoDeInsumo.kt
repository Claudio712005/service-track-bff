package com.clau.service_track.bff.domain.model

import java.math.BigDecimal

data class SaldoDeInsumo(
    val insumoId: String,
    val sku: String,
    val unidadeDeMedida: String,
    val quantidadeDisponivel: BigDecimal,
    val quantidadeReservada: BigDecimal,
)
