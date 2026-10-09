package com.clau.service_track.bff.infra.client.ordens

import java.time.OffsetDateTime

data class FatoDaOrdemResponse(
    val statusAnterior: String? = null,
    val statusNovo: String,
    val transicionou: Boolean = true,
    val motivo: String? = null,
    val ocorridoEm: OffsetDateTime,
)
