package com.clau.service_track.bff.domain.model

enum class StatusDaOrdem {
    RECEBIDA,
    EM_DIAGNOSTICO,
    AGUARDANDO_APROVACAO,
    EM_EXECUCAO,
    FINALIZADA,
    ENTREGUE,
    CANCELADA;

    companion object {
        fun de(nome: String): StatusDaOrdem = entries.find { it.name == nome }
            ?: throw IllegalArgumentException("Status de ordem desconhecido: $nome")
    }
}
