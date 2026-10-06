package com.clau.service_track.bff.domain

data class ClienteVeiculos(
    val cliente: Pessoa,
    val veiculos: List<Veiculo>,
    val veiculosIndisponiveis: Boolean,
)
