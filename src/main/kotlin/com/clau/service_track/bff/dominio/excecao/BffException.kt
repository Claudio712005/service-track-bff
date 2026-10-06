package com.clau.service_track.bff.dominio.excecao

sealed class BffException(
    mensagem: String,
    causa: Throwable? = null,
) : RuntimeException(mensagem, causa)

class RecursoNaoEncontradoException(
    mensagem: String,
) : BffException(mensagem)

class RequisicaoInvalidaException(
    mensagem: String,
) : BffException(mensagem)

class ServicoIndisponivelException(
    val servico: String,
    causa: Throwable? = null,
) : BffException("Servico $servico indisponivel", causa)

class NaoAutorizadoException(
    mensagem: String = "Credencial ausente ou invalida",
) : BffException(mensagem)
