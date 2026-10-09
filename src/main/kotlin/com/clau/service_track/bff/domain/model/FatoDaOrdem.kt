package com.clau.service_track.bff.domain.model

import java.time.OffsetDateTime

data class FatoDaOrdem(
    val statusAnterior: String?,
    val statusNovo: String,
    val transicionou: Boolean,
    val motivo: String?,
    val ocorridoEm: OffsetDateTime,
)
