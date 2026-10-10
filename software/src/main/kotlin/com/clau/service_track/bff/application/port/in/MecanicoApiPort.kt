package com.clau.service_track.bff.application.port.`in`

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

@RequestMapping("/mecanicos")
@Tag(
    name = "Mecânicos",
    description = "Visão de mecânico para o aplicativo. Mecânico é pessoa física e só tem CPF; " +
        "a regra vive no serviço de usuários, não aqui.",
)
interface MecanicoApiPort {

    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "listarMecanicos",
        summary = "Lista os mecânicos ativos",
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
    fun listar(): Flux<Pessoa>

    @GetMapping(path = ["/{id}"], produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "buscarMecanico",
        summary = "Busca o mecânico por identificador",
        description = "Identificador de cliente responde 404 aqui: o recurso é de mecânico.",
    )
    @ApiResponse(
        responseCode = "200",
        description = "Mecânico encontrado.",
        content = [Content(schema = Schema(implementation = Pessoa::class))],
    )
    @ApiResponse(
        responseCode = "404",
        description = "Não existe mecânico com este identificador.",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
    )
    fun buscar(
        @Parameter(description = "Identificador do mecânico.", example = "018f2c9a-5f2e-7c31-9a41-6f3b2d0e9c11")
        @PathVariable
        id: String,
    ): Mono<Pessoa>
}
