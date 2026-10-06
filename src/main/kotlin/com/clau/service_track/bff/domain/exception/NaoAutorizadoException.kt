package com.clau.service_track.bff.domain.exception

class NaoAutorizadoException(
    mensagem: String = "Credencial ausente ou invalida",
) : BffException(mensagem)