package com.clau.service_track.bff.application.port.`in`

import com.clau.service_track.bff.domain.model.ClienteVeiculos
import com.clau.service_track.bff.domain.model.Pessoa
import com.clau.service_track.bff.infra.web.error.ErrorResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@RequestMapping("/clientes")
@Tag(
    name = "Clientes",
    description = "Visão de cliente para o aplicativo. Este recurso não é dono do dado: ele " +
        "compõe a resposta a partir do serviço de usuários e veículos, e existe para o cliente " +
        "não precisar orquestrar duas chamadas.",
)
interface ClienteApiPort {

    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "listarClientes",
        summary = "Lista os clientes ativos",
        description = "Coleção vazia responde 200 com lista vazia, não 404.",
    )
    @ApiResponse(
        responseCode = "200",
        description = "Coleção retornada.",
        content = [
            Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                array = ArraySchema(schema = Schema(implementation = Pessoa::class)),
            ),
        ],
    )
    @ApiResponse(
        responseCode = "503",
        description = "O serviço de usuários não respondeu. Já houve retentativa com espera " +
            "exponencial antes desta resposta.",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    fun listar(): Flux<Pessoa>

    @GetMapping(path = ["/{id}"], produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "buscarClienteComVeiculos",
        summary = "Busca o cliente com os veículos dele",
        description = "Duas chamadas ao serviço de usuários, compostas numa resposta. Se a " +
            "consulta de veículos falhar, a resposta sai com o cliente e `veiculosIndisponiveis` " +
            "em true, em vez de falhar inteira — degrada o acessório e propaga o essencial.",
    )
    @ApiResponse(
        responseCode = "200",
        description = "Cliente encontrado. Veja `veiculosIndisponiveis` antes de concluir que o " +
            "cliente não tem veículo.",
        content = [Content(schema = Schema(implementation = ClienteVeiculos::class))],
    )
    @ApiResponse(
        responseCode = "404",
        description = "Não existe cliente com este identificador. Identificador de mecânico " +
            "também responde 404 aqui.",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    fun buscar(
        @Parameter(description = "Identificador do cliente.", example = "018f2c9a-5f2e-7c31-9a41-6f3b2d0e9c11")
        @PathVariable
        id: String,
    ): Mono<ClienteVeiculos>
}
