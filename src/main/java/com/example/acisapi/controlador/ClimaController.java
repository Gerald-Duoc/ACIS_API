package com.example.acisapi.controlador;

import com.example.acisapi.dto.Respuestas.ClimaDto;
import com.example.acisapi.servicio.ClimaService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Clima real a través de la API (HU-29, HU-30, HU-31). La app nunca llama al servicio del clima
 * directamente: le pregunta a esta API, y la API consulta Open-Meteo.
 */
@RestController
@RequestMapping("/api/clima")
public class ClimaController {

    private final ClimaService clima;

    public ClimaController(ClimaService clima) {
        this.clima = clima;
    }

    /** Pronóstico para unas coordenadas: /api/clima?lat=-33.41&lon=-70.66&fecha=2026-11-24&exterior=true */
    @GetMapping
    public ClimaDto porCoordenadas(
            @RequestParam("lat") double latitud,
            @RequestParam("lon") double longitud,
            @RequestParam("fecha") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(name = "exterior", defaultValue = "false") boolean exterior) {
        return clima.consultar(latitud, longitud, fecha, exterior);
    }

    /** Pronóstico para un local (HU-31: advertencia al agendar una mantención). */
    @GetMapping("/locales/{localId}")
    public ClimaDto porLocal(
            @PathVariable("localId") Long localId,
            @RequestParam("fecha") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(name = "exterior", defaultValue = "false") boolean exterior) {
        return clima.consultarLocal(localId, fecha, exterior);
    }
}
