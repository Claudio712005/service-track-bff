package com.clau.service_track.bff.domain.model

data class OrdemComposta(
    val ordem: OrdemServico,
    val cliente: Pessoa?,
    val mecanico: Pessoa?,
    val veiculo: Veiculo?,
    val parciaisIndisponiveis: List<String>,
)
