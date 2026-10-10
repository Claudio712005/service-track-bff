package com.clau.service_track.bff.infra.config

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.time.Instant
import java.util.Base64
import java.util.Date
import kotlin.test.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(
    properties = [
        "servicetrack.security.jwt.enabled=true",
        "servicetrack.security.jwt.public-key=file:build/tmp/teste-de-seguranca/publicKey.pem",
    ],
)
class SecurityConfigTest {

    @Autowired
    private lateinit var contexto: ApplicationContext

    private val cliente: WebTestClient by lazy {
        WebTestClient.bindToApplicationContext(contexto).build()
    }

    private fun token(expiraEm: Instant = Instant.now().plusSeconds(300)): String {
        val conjunto = JWTClaimsSet.Builder()
            .subject("52998224725")
            .issuer("service-track-lambda")
            .issueTime(Date.from(Instant.now().minusSeconds(10)))
            .expirationTime(Date.from(expiraEm))
            .build()

        val assinado = SignedJWT(JWSHeader(JWSAlgorithm.RS256), conjunto)
        assinado.sign(RSASSASigner(privada))
        return assinado.serialize()
    }

    @Test
    fun `rota de negocio sem credencial responde 401`() {
        cliente.get().uri("/clientes").exchange().expectStatus().isUnauthorized
        cliente.get().uri("/catalogo/servicos").exchange().expectStatus().isUnauthorized
        cliente.get().uri("/ordens/qualquer").exchange().expectStatus().isUnauthorized
    }

    @Test
    fun `credencial assinada por outra chave e recusada`() {
        val outraChave = KeyPairGenerator.getInstance("RSA")
            .apply { initialize(2048) }
            .generateKeyPair()

        val conjunto = JWTClaimsSet.Builder()
            .subject("52998224725")
            .expirationTime(Date.from(Instant.now().plusSeconds(300)))
            .build()
        val assinado = SignedJWT(JWSHeader(JWSAlgorithm.RS256), conjunto)
        assinado.sign(RSASSASigner(outraChave.private as RSAPrivateKey))

        cliente.get().uri("/clientes")
            .header("Authorization", "Bearer ${assinado.serialize()}")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `credencial expirada e recusada`() {
        cliente.get().uri("/clientes")
            .header("Authorization", "Bearer ${token(expiraEm = Instant.now().minusSeconds(60))}")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `credencial valida atravessa, e a falha vem da dependencia, nao da seguranca`() {
        cliente.get().uri("/clientes")
            .header("Authorization", "Bearer ${token()}")
            .exchange()
            .expectStatus().value { status ->
                require(status != 401 && status != 403) {
                    "credencial valida foi recusada pela seguranca, status=$status"
                }
            }
    }

    @Test
    fun `saude e contrato ficam abertos, porque sonda e documentacao nao tem token`() {
        cliente.get().uri("/actuator/health").exchange().expectStatus().isOk
        cliente.get().uri("/actuator/health/readiness").exchange().expectStatus().isOk
        cliente.get().uri("/v3/api-docs").exchange().expectStatus().isOk
    }

    private companion object {

        private val par = KeyPairGenerator.getInstance("RSA")
            .apply { initialize(2048) }
            .generateKeyPair()

        val privada = par.private as RSAPrivateKey

        init {
            val destino = Path.of("build/tmp/teste-de-seguranca/publicKey.pem")
            Files.createDirectories(destino.parent)

            val corpo = Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(par.public.encoded)
            Files.writeString(
                destino,
                "-----BEGIN PUBLIC KEY-----\n$corpo\n-----END PUBLIC KEY-----\n",
            )
        }
    }
}
