package com.clau.service_track.bff.infra.client.fake

import com.clau.service_track.bff.application.port.out.UsuariosPort
import com.clau.service_track.bff.domain.exception.ServicoIndisponivelException
import com.clau.service_track.bff.domain.model.Pessoa
import com.clau.service_track.bff.domain.model.TipoDeUsuario
import com.clau.service_track.bff.domain.model.Veiculo
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Component
@Profile("dev")
class UsuariosFakeAdapter : UsuariosPort {

    private val log = LoggerFactory.getLogger(UsuariosFakeAdapter::class.java)

    init {
        log.warn("perfil dev ativo: o servico de usuarios esta mocado em memoria, nada sai pela rede")
    }

    override fun listarPorTipo(tipo: TipoDeUsuario): Flux<Pessoa> =
        Flux.fromIterable(PESSOAS.values.filter { it.tipoDeUsuario == tipo })

    override fun buscarPorId(id: String): Mono<Pessoa> {
        if (id == ID_QUE_FALHA) {
            return Mono.error(ServicoIndisponivelException("usuarios"))
        }
        return Mono.justOrEmpty(PESSOAS[id])
    }

    override fun listarVeiculosDoCliente(clienteId: String): Flux<Veiculo> {
        if (clienteId == ID_SEM_VEICULOS) {
            return Flux.error(ServicoIndisponivelException("usuarios"))
        }
        return Flux.fromIterable(VEICULOS[clienteId].orEmpty())
    }

    private companion object {

        const val ID_CLIENTE = "11111111-1111-4111-8111-111111111111"
        const val ID_SEM_VEICULOS = "22222222-2222-4222-8222-222222222222"
        const val ID_MECANICO = "33333333-3333-4333-8333-333333333333"
        const val ID_QUE_FALHA = "99999999-9999-4999-8999-999999999999"

        val PESSOAS = mapOf(
            ID_CLIENTE to Pessoa(
                id = ID_CLIENTE,
                documento = "52998224725",
                nome = "Joana Ferreira",
                email = "joana@exemplo.test",
                tipoDeUsuario = TipoDeUsuario.CLIENTE,
            ),
            ID_SEM_VEICULOS to Pessoa(
                id = ID_SEM_VEICULOS,
                documento = "11144477735",
                nome = "Carlos Dias",
                email = "carlos@exemplo.test",
                tipoDeUsuario = TipoDeUsuario.CLIENTE,
            ),
            ID_MECANICO to Pessoa(
                id = ID_MECANICO,
                documento = "15350946056",
                nome = "Rafael Lima",
                email = "rafael@exemplo.test",
                tipoDeUsuario = TipoDeUsuario.MECANICO,
            ),
        )

        val VEICULOS = mapOf(
            ID_CLIENTE to listOf(
                Veiculo(
                    id = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                    placa = "ABC1D23",
                    marca = "Fiat",
                    modelo = "Argo",
                    anoModelo = 2023,
                ),
                Veiculo(
                    id = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
                    placa = "XYZ9A88",
                    marca = "Volkswagen",
                    modelo = "Polo",
                    anoModelo = 2021,
                ),
            ),
        )
    }
}
