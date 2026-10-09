package com.clau.service_track.bff.infra.client.ordens

import java.math.BigDecimal
import java.time.LocalDateTime

data class OrdemServicoResponse(
    val id: String,
    val motivo: String,
    val observacao: String = "",
    val clienteId: String,
    val mecanicoId: String,
    val veiculoId: String,
    val status: String,
    val prazoConclusao: LocalDateTime? = null,
    val orcamento: OrcamentoResponse? = null,
    val itensServico: List<ItemDeServicoResponse> = emptyList(),
    val itensInsumo: List<ItemDeInsumoResponse> = emptyList(),
    val dataCriacao: LocalDateTime,
    val dataAtualizacao: LocalDateTime,
)

data class OrcamentoResponse(
    val id: String,
    val custoMaoDeObra: BigDecimal,
    val custoInsumos: BigDecimal,
    val valorTotal: BigDecimal,
    val aprovado: Boolean,
)

data class ItemDeServicoResponse(
    val id: String,
    val servicoId: String,
    val valor: BigDecimal,
    val feito: Boolean = false,
)

data class ItemDeInsumoResponse(
    val id: String,
    val insumoId: String,
    val quantidade: BigDecimal,
)
