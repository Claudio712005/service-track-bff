package com.clau.service_track.bff.infra.config

import com.clau.service_track.bff.infra.web.MdcThreadLocalAccessor
import com.clau.service_track.bff.infra.web.CorrelationFilter
import io.micrometer.context.ContextRegistry
import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Configuration
import reactor.core.publisher.Hooks

@Configuration
class CorrelationContextConfig {

    @PostConstruct
    fun registrar() {
        Hooks.enableAutomaticContextPropagation()

        val registro = ContextRegistry.getInstance()
        registro.registerThreadLocalAccessor(MdcThreadLocalAccessor(CorrelationFilter.CHAVE_CORRELACAO))
        registro.registerThreadLocalAccessor(MdcThreadLocalAccessor(CorrelationFilter.CHAVE_REQUISICAO))
    }
}
