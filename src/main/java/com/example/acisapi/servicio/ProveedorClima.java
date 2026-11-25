package com.example.acisapi.servicio;

import java.time.LocalDate;

/**
 * Origen de los datos del clima. Hoy lo implementa Open-Meteo, pero al ser una interfaz
 * las pruebas usan una versión falsa y no necesitan internet.
 */
public interface ProveedorClima {

    PronosticoDia pronostico(double latitud, double longitud, LocalDate fecha);
}
