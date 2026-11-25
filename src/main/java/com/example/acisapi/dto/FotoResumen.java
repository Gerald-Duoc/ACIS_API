package com.example.acisapi.dto;

import com.example.acisapi.modelo.TipoFoto;
import java.time.LocalDateTime;

/**
 * Datos de una foto SIN la imagen (los bytes se descargan aparte con GET /api/fotos/{id}).
 * Es un record independiente porque lo usa una consulta JPQL del repositorio.
 */
public record FotoResumen(
        Long id,
        TipoFoto tipo,
        String nombreArchivo,
        String contentType,
        Long tamano,
        LocalDateTime creadaEn) {
}
