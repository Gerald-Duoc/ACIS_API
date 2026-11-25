package com.example.acisapi.servicio;

import java.time.LocalDate;

/** Pronóstico crudo de un día, tal como lo entrega el servicio del clima. */
public record PronosticoDia(
        LocalDate fecha,
        double temperaturaMaxC,
        double precipitacionMm,
        int probabilidadLluviaPct,
        double vientoMaxKmh,
        double rafagaMaxKmh) {
}
