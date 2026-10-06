package com.clau.service_track.bff.infraestrutura.usuarios

import com.clau.service_track.bff.dominio.Pessoa
import com.clau.service_track.bff.dominio.TipoDeUsuario
import com.clau.service_track.bff.dominio.Veiculo
import com.clau.service_track.bff.dominio.excecao.RecursoNaoEncontradoException
import com.clau.service_track.bff.dominio.excecao.ServicoIndisponivelException
import com.clau.service_track.bff.dominio.porta.UsuariosPort
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator
import io.github.resilience4j.reactor.retry.RetryOperator
import io.github.resilience4j.retry.Retry
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono

@Component
class UsuariosHttpAdapter(
    private val webClientDeUsuarios: WebClient,
    private val retryDeUsuarios: Retry,
    private val disjuntorDeUsuarios: CircuitBreaker,
) : UsuariosPort {

    private val log = LoggerFactory.getLogger(UsuariosHttpAdapter::class.java)

    override suspend fun listarPorTipo(tipo: TipoDeUsuario): List<Pessoa> {
        val resposta = executar(ROTA_DE_USUARIOS) {
            webClientDeUsuarios.get()
                .uri { construtor -> construtor.path(ROTA_DE_USUARIOS).queryParam("tipo", tipo.name).build() }
                .retrieve()
                .bodyToMono(LISTA_DE_USUARIOS)
        }
        return resposta.orEmpty().map(::paraPessoa)
    }

    override suspend fun buscarPorId(id: String): Pessoa {
        val resposta = executar("$ROTA_DE_USUARIOS/{id}") {
            webClientDeUsuarios.get()
                .uri("$ROTA_DE_USUARIOS/{id}", id)
                .retrieve()
                .bodyToMono(UsuarioResponse::class.java)
        } ?: throw RecursoNaoEncontradoException("Usuario $id nao encontrado")

        return paraPessoa(resposta)
    }

    override suspend fun listarVeiculosDoCliente(clienteId: String): List<Veiculo> {
        val resposta = executar(ROTA_DE_VEICULOS) {
            webClientDeUsuarios.get()
                .uri { construtor -> construtor.path(ROTA_DE_VEICULOS).queryParam("clienteId", clienteId).build() }
                .retrieve()
                .bodyToMono(LISTA_DE_VEICULOS)
        }
        return resposta.orEmpty().map(::paraVeiculo)
    }

    private suspend fun <T : Any> executar(rota: String, chamada: () -> Mono<T>): T? =
        Mono.defer(chamada)
            .onErrorMap { erro -> traduzir(rota, erro) }
            .transformDeferred(CircuitBreakerOperator.of(disjuntorDeUsuarios))
            .transformDeferred(RetryOperator.of(retryDeUsuarios))
            .onErrorResume(RecursoNaoEncontradoException::class.java) { Mono.empty() }
            .awaitSingleOrNull()

    private fun traduzir(rota: String, erro: Throwable): Throwable = when {
        erro is WebClientResponseException && erro.statusCode.value() == NAO_ENCONTRADO ->
            RecursoNaoEncontradoException("Recurso nao encontrado em $rota")

        erro is WebClientResponseException -> {
            log.warn("servico de usuarios respondeu erro rota={} status={}", rota, erro.statusCode.value())
            ServicoIndisponivelException("usuarios", erro)
        }

        erro is WebClientRequestException -> {
            log.warn("falha de rede ao chamar usuarios rota={} causa={}", rota, erro.message)
            ServicoIndisponivelException("usuarios", erro)
        }

        else -> erro
    }

    private fun paraPessoa(resposta: UsuarioResponse) = Pessoa(
        id = resposta.id,
        documento = resposta.documento,
        nome = resposta.nome,
        email = resposta.email,
        tipoDeUsuario = TipoDeUsuario.valueOf(resposta.tipoDeUsuario),
    )

    private fun paraVeiculo(resposta: VeiculoResponse) = Veiculo(
        id = resposta.id,
        placa = resposta.placa,
        marca = resposta.marca,
        modelo = resposta.modelo,
        anoModelo = resposta.anoModelo,
    )

    private companion object {
        const val ROTA_DE_USUARIOS = "/usuarios"
        const val ROTA_DE_VEICULOS = "/veiculos"
        const val NAO_ENCONTRADO = 404
        val LISTA_DE_USUARIOS = object : ParameterizedTypeReference<List<UsuarioResponse>>() {}
        val LISTA_DE_VEICULOS = object : ParameterizedTypeReference<List<VeiculoResponse>>() {}
    }
}
