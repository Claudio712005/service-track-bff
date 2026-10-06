package com.clau.service_track.bff.domain.exception

class ServicoIndisponivelException(
    val servico: String,
    causa: Throwable? = null,
) : BffException("Servico $servico indisponivel", causa)