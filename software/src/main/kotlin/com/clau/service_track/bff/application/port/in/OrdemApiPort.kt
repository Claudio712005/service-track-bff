package com.clau.service_track.bff.application.port.`in`

import com.clau.service_track.bff.domain.model.FatoDaOrdem
import com.clau.service_track.bff.domain.model.OrdemComposta
import com.clau.service_track.bff.domain.model.OrdemServico
import com.clau.service_track.bff.domain.model.PedidoDeAbertura
import com.clau.service_track.bff.domain.model.Saga
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Tag(
    name = "Ordens de serviço",
    description = "Composição da ordem com cliente, veículo, serviços e insumos resolvidos. " +
        "Este serviço não guarda nada: cada campo vem do serviço que é dono dele."
)
@RequestMapping("/ordens")
interface OrdemApiPort {

    @Operation(
        summary = "Abre uma ordem de serviço",
        description = "Repassa ao serviço de ordens. Cliente, mecânico e veículo não são validados aqui — " +
            "quem valida é quem é dono do dado."
    )
    @ApiResponse(responseCode = "201", description = "Ordem aberta")
    @PostMapping
    fun abrir(@Valid @RequestBody requisicao: AbrirOrdemRequest): Mono<ResponseEntity<OrdemServico>>

    @Operation(
        summary = "Detalha uma ordem, com os dados dos outros serviços resolvidos",
        description = "Busca cliente, mecânico, veículo, serviços e insumos **em paralelo**. Serviço " +
            "indisponível não derruba a resposta: o campo vem nulo e o nome dele aparece em " +
            "`parciaisIndisponiveis`, para o cliente saber que a resposta está incompleta."
    )
    @ApiResponse(responseCode = "200", description = "Ordem encontrada, possivelmente com parciais ausentes")
    @ApiResponse(responseCode = "404", description = "Ordem inexistente", content = [])
    @GetMapping("/{id}")
    fun detalhar(@PathVariable id: String): Mono<OrdemComposta>

    @Operation(summary = "Lista as ordens de um cliente")
    @GetMapping
    fun listarDoCliente(@RequestParam clienteId: String): Flux<OrdemServico>

    @Operation(
        summary = "Aprova o orçamento e dispara a saga de reserva",
        description = "A ordem **não** avança de estado na resposta: ela segue em AGUARDANDO_APROVACAO até " +
            "o catálogo confirmar a reserva de todos os insumos. Acompanhe em `GET /ordens/{id}/saga`."
    )
    @PostMapping("/{id}/orcamento/aprovacao")
    fun aprovarOrcamento(@PathVariable id: String): Mono<OrdemServico>

    @Operation(
        summary = "Linha do tempo da ordem",
        description = "Transições de estado e fatos sem transição, como bloqueio esperando reposição de insumo. " +
            "Quem desenha só a régua de estados filtra por `transicionou`."
    )
    @GetMapping("/{id}/historico")
    fun historico(@PathVariable id: String): Flux<FatoDaOrdem>

    @Operation(
        summary = "Progresso da transação distribuída",
        description = "Devolve lista vazia quando o serviço de ordens não expõe saga para esta ordem, em vez de erro."
    )
    @GetMapping("/{id}/saga")
    fun saga(@PathVariable id: String): Flux<Saga>
}
