package com.clau.service_track.bff.domain.model

data class Saga(
    val tipo: String,
    val situacao: String,
    val etapa: String,
    val motivo: String?,
    val passos: List<PassoDaSaga>,
)
