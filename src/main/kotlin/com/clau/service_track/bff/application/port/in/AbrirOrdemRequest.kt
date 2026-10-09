package com.clau.service_track.bff.application.port.`in`

import com.clau.service_track.bff.domain.model.PedidoDeAbertura
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Future
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

@Schema(name = "AbrirOrdemRequest", description = "Dados de abertura de uma ordem de serviço.")
data class AbrirOrdemRequest(

    @get:Schema(description = "Por que o veículo entrou na oficina.")
    @get:NotBlank
    @get:Size(max = 500)
    val motivo: String,

    @get:Pattern(regexp = UUID, message = "deve ser um UUID")
    val clienteId: String,

    @get:Pattern(regexp = UUID, message = "deve ser um UUID")
    val mecanicoId: String,

    @get:Pattern(regexp = UUID, message = "deve ser um UUID")
    val veiculoId: String,

    @get:Size(max = 2000)
    val observacao: String = "",

    @get:Schema(description = "Prazo prometido ao cliente.", nullable = true)
    @get:Future
    val prazoConclusao: LocalDateTime? = null,
) {

    fun paraPedido() = PedidoDeAbertura(
        motivo = motivo,
        clienteId = clienteId,
        mecanicoId = mecanicoId,
        veiculoId = veiculoId,
        observacao = observacao,
        prazoConclusao = prazoConclusao,
    )

    private companion object {
        const val UUID = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    }
}
