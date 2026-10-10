package com.clau.service_track.bff.infra.client.catalogo

import java.math.BigDecimal

data class SaldoDeInsumoResponse(
    val insumoId: String,
    val sku: String,
    val unidadeDeMedida: String,
    val quantidadeDisponivel: BigDecimal,
    val quantidadeReservada: BigDecimal,
)
