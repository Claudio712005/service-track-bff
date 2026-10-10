package com.clau.service_track.bff.infra.config

import com.clau.service_track.bff.infra.web.filter.CorrelationFilter
import com.clau.service_track.bff.infra.web.filter.MdcThreadLocalAccessor
import io.micrometer.context.ContextRegistry
import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Configuration
import reactor.core.publisher.Hooks

@Configuration
class ReactorContextConfig {

    @PostConstruct
    fun bridgeReactorContextToLoggingThreadLocals() {
        Hooks.enableAutomaticContextPropagation()

        val registry = ContextRegistry.getInstance()
        registry.registerThreadLocalAccessor(MdcThreadLocalAccessor(CorrelationFilter.CORRELATION_FIELD))
        registry.registerThreadLocalAccessor(MdcThreadLocalAccessor(CorrelationFilter.REQUEST_FIELD))
    }
}
