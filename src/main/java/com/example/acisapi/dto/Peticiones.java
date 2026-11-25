package com.example.acisapi.dto;

import com.example.acisapi.modelo.Prioridad;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.List;

/**
 * Datos que la app envía a la API (cuerpos JSON).
 * Las anotaciones (@NotBlank, @NotNull...) validan lo que llega; si algo falta, la API
 * responde 400 con el detalle (HU-06, HU-16, HU-32, etc.).
 */
public final class Peticiones {

    private Peticiones() {
    }

    // --- Acceso (HU-01) ---
    public record LoginRequest(
            @NotBlank(message = "El correo es obligatorio") String correo,
            @NotBlank(message = "La clave es obligatoria") String clave) {
    }

    // --- Catastro (HU-02, HU-32, HU-33, HU-34, HU-36) ---
    public record ClienteRequest(
            @NotBlank(message = "La razón social es obligatoria") String nombre,
            @NotBlank(message = "El contacto es obligatorio") String contacto,
            @NotNull(message = "El SLA es obligatorio") @Min(value = 1, message = "El SLA debe ser de al menos 1 hora") Integer slaHoras) {
    }

    public record LocalRequest(
            @NotBlank(message = "El nombre del local es obligatorio") String nombre,
            @NotBlank(message = "La dirección es obligatoria") String direccion,
            @NotNull(message = "La latitud es obligatoria") Double latitud,
            @NotNull(message = "La longitud es obligatoria") Double longitud) {
    }

    public record EquipoRequest(
            @NotBlank(message = "El nombre del equipo es obligatorio") String nombre,
            @NotBlank(message = "El tipo es obligatorio") String tipo,
            @NotBlank(message = "La marca es obligatoria") String marca,
            @NotBlank(message = "El modelo es obligatorio") String modelo,
            @NotNull(message = "Indica si el equipo está en el exterior") Boolean exterior,
            String ubicacion) {
    }

    public record TecnicoRequest(
            @NotBlank(message = "El nombre es obligatorio") String nombre,
            @NotBlank(message = "El correo es obligatorio") @Email(message = "El correo no es válido") String correo,
            String clave) {
    }

    // --- Planificación (HU-06 a HU-10) ---
    public record OrdenRequest(
            @NotNull(message = "El equipo es obligatorio") Long equipoId,
            @NotBlank(message = "El tipo es obligatorio")
            @Pattern(regexp = "Preventiva|Correctiva", message = "El tipo debe ser Preventiva o Correctiva") String tipo,
            @NotNull(message = "La prioridad es obligatoria") Prioridad prioridad,
            @NotNull(message = "La fecha es obligatoria") LocalDate fecha,
            Long tecnicoId,
            @NotBlank(message = "La descripción es obligatoria") String descripcion) {
    }

    public record AsignarRequest(
            @NotNull(message = "El técnico es obligatorio") Long tecnicoId) {
    }

    public record ReprogramarRequest(
            @NotNull(message = "La fecha es obligatoria") LocalDate fecha) {
    }

    // --- Técnico en terreno (HU-15 a HU-25) ---
    public record MotivoRequest(
            @NotBlank(message = "El motivo es obligatorio") String motivo) {
    }

    public record CheckInRequest(
            @NotNull(message = "Indica si autorizas el uso de tu ubicación") Boolean ubicacionAutorizada) {
    }

    public record RegistroRequest(String diagnostico, List<String> actividades, List<String> materiales) {
    }

    // --- Cliente (HU-40 a HU-43) ---
    public record SolicitudRequest(
            @NotNull(message = "El equipo es obligatorio") Long equipoId,
            @NotBlank(message = "La descripción es obligatoria") String descripcion) {
    }

    public record ComentarioRequest(
            @NotBlank(message = "El comentario es obligatorio") String comentario) {
    }
}
