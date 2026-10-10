package com.clau.service_track.bff.infra.config

import java.time.Duration

interface ServiceProperties {

    val baseUrl: String

    val connectTimeout: Duration

    val responseTimeout: Duration
}
