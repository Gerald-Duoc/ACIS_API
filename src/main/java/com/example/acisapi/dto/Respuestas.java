package com.example.acisapi.dto;

import com.example.acisapi.modelo.Clima;
import com.example.acisapi.modelo.Conformidad;
import com.example.acisapi.modelo.EstadoOt;
import com.example.acisapi.modelo.Perfil;
import com.example.acisapi.modelo.Prioridad;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Datos que la API devuelve a la app (JSON).
 * Los nombres de los enums (EstadoOt, Prioridad, Clima, Conformidad) son los mismos
 * que usa la app Android, para que se puedan leer directamente.
 */
public final class Respuestas {

    private Respuestas() {
    }

    public record UsuarioDto(Long id, String nombre, String correo, Perfil perfil, Long clienteId, Long localId) {
    }

    public record LoginResponse(String token, LocalDateTime expiraEn, UsuarioDto usuario) {
    }

    public record EquipoDto(Long id, Long localId, String nombre, String tipo, String marca, String modelo,
                            boolean exterior, String ubicacion, LocalDate ultimaMantencion) {
    }

    public record LocalDto(Long id, Long clienteId, String nombre, String direccion,
                           Double latitud, Double longitud, List<EquipoDto> equipos) {
    }

    public record ClienteDto(Long id, String nombre, String contacto, Integer slaHoras, List<LocalDto> locales) {
    }

    public record TecnicoDto(Long id, String nombre, String correo, long otActivas) {
    }

    /** Una intervención anterior de un equipo (HU-35). */
    public record HistorialItem(String codigo, LocalDate fecha, String tipo, String diagnostico) {
    }

    public record OrdenDto(
            Long id,
            String codigo,
            EstadoOt estado,
            Prioridad prioridad,
            String tipo,
            LocalDate fecha,
            String descripcion,
            String observacion,
            Integer slaHoras,
            Integer tiempoRealMin,
            Long clienteId,
            String clienteNombre,
            Long localId,
            String localNombre,
            String direccion,
            Long equipoId,
            String equipoNombre,
            boolean equipoExterior,
            String ubicacionEquipo,
            LocalDate ultimaMantencion,
            Long tecnicoId,
            String tecnicoNombre,
            boolean ubicacionAutorizada,
            LocalDateTime checkIn,
            LocalDateTime checkOut,
            String diagnostico,
            List<String> actividades,
            List<String> materiales,
            long fotosAntes,
            long fotosDespues,
            Conformidad conformidad,
            LocalDateTime fechaConformidad,
            String comentarioCliente) {
    }

    /** Pronóstico de un día y su clasificación (HU-29, HU-30, HU-31). */
    public record ClimaDto(
            LocalDate fecha,
            Clima clima,
            boolean lluvia,
            boolean vientoFuerte,
            boolean requiereAlerta,
            double temperaturaMaxC,
            double precipitacionMm,
            int probabilidadLluviaPct,
            double vientoMaxKmh,
            double rafagaMaxKmh,
            String fuente) {
    }

    public record ErrorRespuesta(int estado, String error, String mensaje, LocalDateTime fecha) {
    }

    public record SaludDto(String estado, LocalDateTime hora) {
    }
}
