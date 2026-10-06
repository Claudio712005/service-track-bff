package com.clau.service_track.bff.infraestrutura.config

import com.clau.service_track.bff.infraestrutura.web.AcessorDeMdc
import com.clau.service_track.bff.infraestrutura.web.CorrelacaoFilter
import io.micrometer.context.ContextRegistry
import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Configuration
import reactor.core.publisher.Hooks

@Configuration
class ContextoDeCorrelacaoConfig {

    @PostConstruct
    fun registrar() {
        Hooks.enableAutomaticContextPropagation()

        val registro = ContextRegistry.getInstance()
        registro.registerThreadLocalAccessor(AcessorDeMdc(CorrelacaoFilter.CHAVE_CORRELACAO))
        registro.registerThreadLocalAccessor(AcessorDeMdc(CorrelacaoFilter.CHAVE_REQUISICAO))
    }
}
