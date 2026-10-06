package com.clau.service_track.bff.infraestrutura.web

import com.clau.service_track.bff.aplicacao.ConsultaDeMecanicos
import com.clau.service_track.bff.dominio.Pessoa
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/mecanicos")
class MecanicoController(
    private val consulta: ConsultaDeMecanicos,
) {

    @GetMapping
    suspend fun listar(): List<Pessoa> = consulta.listar()

    @GetMapping("/{id}")
    suspend fun buscar(@PathVariable id: String): Pessoa = consulta.buscar(id)
}
