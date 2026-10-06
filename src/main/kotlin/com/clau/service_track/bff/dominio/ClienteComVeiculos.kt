package com.clau.service_track.bff.dominio

data class ClienteComVeiculos(
    val cliente: Pessoa,
    val veiculos: List<Veiculo>,
    val veiculosIndisponiveis: Boolean,
)
