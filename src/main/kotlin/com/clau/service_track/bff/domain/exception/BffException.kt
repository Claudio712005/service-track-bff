package com.clau.service_track.bff.domain.exception

sealed class BffException(
    mensagem: String,
    causa: Throwable? = null,
) : RuntimeException(mensagem, causa)