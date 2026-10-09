package com.clau.service_track.bff.infra.client.ordens

import java.math.BigDecimal

data class SagaResponse(
    val tipo: String,
    val situacao: String,
    val etapa: String,
    val motivo: String? = null,
    val passos: List<PassoDaSagaResponse> = emptyList(),
)

data class PassoDaSagaResponse(
    val insumoId: String,
    val etapa: String,
    val quantidade: BigDecimal,
    val situacao: String,
    val motivo: String? = null,
)
