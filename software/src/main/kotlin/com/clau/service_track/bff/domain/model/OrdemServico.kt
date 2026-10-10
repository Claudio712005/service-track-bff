package com.clau.service_track.bff.domain.model

import java.time.LocalDateTime

data class OrdemServico(
    val id: String,
    val motivo: String,
    val observacao: String,
    val status: StatusDaOrdem,
    val clienteId: String,
    val mecanicoId: String,
    val veiculoId: String,
    val prazoConclusao: LocalDateTime?,
    val orcamento: Orcamento?,
    val itensServico: List<ItemDeServico>,
    val itensInsumo: List<ItemDeInsumo>,
    val dataCriacao: LocalDateTime,
    val dataAtualizacao: LocalDateTime,
)
