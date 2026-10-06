package com.clau.service_track.bff.infra.web.filter

import io.micrometer.context.ThreadLocalAccessor
import org.slf4j.MDC

class MdcThreadLocalAccessor(private val field: String) : ThreadLocalAccessor<String> {

    override fun key(): Any = field

    override fun getValue(): String? = MDC.get(field)

    override fun setValue(value: String) {
        MDC.put(field, value)
    }

    override fun setValue() {
        MDC.remove(field)
    }
}
