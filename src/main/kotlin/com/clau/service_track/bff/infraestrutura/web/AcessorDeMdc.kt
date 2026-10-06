package com.clau.service_track.bff.infraestrutura.web

import io.micrometer.context.ThreadLocalAccessor
import org.slf4j.MDC

class AcessorDeMdc(private val chave: String) : ThreadLocalAccessor<String> {

    override fun key(): Any = chave

    override fun getValue(): String? = MDC.get(chave)

    override fun setValue(valor: String) {
        MDC.put(chave, valor)
    }

    override fun setValue() {
        MDC.remove(chave)
    }
}
