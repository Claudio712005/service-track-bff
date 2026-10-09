package com.clau.service_track.bff

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(properties = ["servicetrack.security.jwt.enabled=false"])
class ApplicationTests {

    @Test
    fun contextLoads() {
    }

}
