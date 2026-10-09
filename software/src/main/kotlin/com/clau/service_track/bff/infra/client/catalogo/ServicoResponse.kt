package com.clau.service_track.bff.infra.client.catalogo

import java.math.BigDecimal

data class ServicoResponse(
    val id: String,
    val nome: String,
    val valorReferencia: BigDecimal? = null,
    val ativo: Boolean = true,
)
