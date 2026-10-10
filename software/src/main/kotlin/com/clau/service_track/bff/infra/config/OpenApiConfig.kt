package com.clau.service_track.bff.infra.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun openApi(): OpenAPI = OpenAPI().info(
        Info()
            .title("ServiceTrack BFF")
            .version("v1")
            .description(
                "Entrada pública da plataforma ServiceTrack. Compõe respostas a partir dos " +
                    "microsserviços e não guarda estado: não há banco por trás deste serviço.\n\n" +
                    "Toda resposta devolve `X-Correlation-Id` e `X-Request-Id`. Mande o primeiro " +
                    "para amarrar sua chamada ao rastro no servidor; se não mandar, ele é gerado.\n\n" +
                    "Erro de dependência vira `503` depois de retentativa com espera exponencial, " +
                    "e o corpo de erro carrega a correlação para busca no log.",
            )
            .license(License().name("Uso educacional").url("https://github.com/Claudio712005")),
    )
}
