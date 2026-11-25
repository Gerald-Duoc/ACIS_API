package com.example.acisapi.servicio;

import com.example.acisapi.dto.FotoResumen;
import com.example.acisapi.error.ApiException;
import com.example.acisapi.modelo.EstadoOt;
import com.example.acisapi.modelo.FotoOrden;
import com.example.acisapi.modelo.OrdenTrabajo;
import com.example.acisapi.modelo.TipoFoto;
import com.example.acisapi.modelo.Usuario;
import com.example.acisapi.repositorio.FotoRepository;
import com.example.acisapi.repositorio.OrdenRepository;
import com.example.acisapi.seguridad.Permisos;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * HU-20 y HU-21: guarda las fotografías de una OT dentro de la base de datos.
 * Solo el técnico asignado puede subirlas, y solo mientras la OT está En ejecución.
 */
@Service
public class FotoService {

    static final Set<String> TIPOS_PERMITIDOS = Set.of("image/jpeg", "image/png", "image/webp");

    private final FotoRepository fotos;
    private final OrdenRepository ordenes;
    private final long maxBytes;

    public FotoService(FotoRepository fotos,
                       OrdenRepository ordenes,
                       @Value("${acis.foto.max-bytes:5242880}") long maxBytes) {
        this.fotos = fotos;
        this.ordenes = ordenes;
        this.maxBytes = maxBytes;
    }

    @Transactional
    public FotoResumen subir(Usuario tecnico, Long ordenId, TipoFoto tipo, MultipartFile archivo) {
        OrdenTrabajo orden = buscarOrden(ordenId);
        if (!Permisos.esTecnicoAsignado(tecnico, orden)) {
            throw ApiException.prohibido("Esta OT no está asignada a ti.");
        }
        if (orden.getEstado() != EstadoOt.EN_EJECUCION) {
            throw ApiException.conflicto("Solo se pueden subir fotos mientras la OT está \""
                    + EstadoOt.EN_EJECUCION.etiqueta() + "\".");
        }
        if (archivo == null || archivo.isEmpty()) {
            throw ApiException.invalido("Debes adjuntar una fotografía.");
        }
        validarTamano(archivo.getSize());
        String tipoContenido = archivo.getContentType();
        if (tipoContenido == null || !TIPOS_PERMITIDOS.contains(tipoContenido)) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Solo se aceptan imágenes JPEG, PNG o WEBP.");
        }

        byte[] datos;
        try {
            datos = archivo.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo leer la fotografía enviada.");
        }
        if (!coincideConFormato(datos, tipoContenido)) {
            throw ApiException.invalido("El archivo no es una imagen válida.");
        }

        FotoOrden foto = fotos.save(new FotoOrden(orden, tipo, nombreSeguro(archivo.getOriginalFilename()),
                tipoContenido, (long) datos.length, LocalDateTime.now(), datos));
        return new FotoResumen(foto.getId(), foto.getTipo(), foto.getNombreArchivo(), foto.getContentType(),
                foto.getTamano(), foto.getCreadaEn());
    }

    @Transactional(readOnly = true)
    public List<FotoResumen> listar(Usuario usuario, Long ordenId) {
        OrdenTrabajo orden = buscarOrden(ordenId);
        if (!Permisos.puedeVer(usuario, orden)) {
            throw ApiException.noEncontrado("La OT no existe.");
        }
        return fotos.resumenes(ordenId);
    }

    /** Devuelve la foto completa (con sus bytes) si el usuario puede ver su OT. */
    @Transactional(readOnly = true)
    public FotoOrden obtener(Usuario usuario, Long fotoId) {
        FotoOrden foto = fotos.findById(fotoId).orElseThrow(() -> ApiException.noEncontrado("La fotografía no existe."));
        if (!Permisos.puedeVer(usuario, foto.getOrden())) {
            throw ApiException.noEncontrado("La fotografía no existe.");
        }
        return foto;
    }

    @Transactional
    public void eliminar(Usuario tecnico, Long fotoId) {
        FotoOrden foto = fotos.findById(fotoId).orElseThrow(() -> ApiException.noEncontrado("La fotografía no existe."));
        OrdenTrabajo orden = foto.getOrden();
        if (!Permisos.esTecnicoAsignado(tecnico, orden)) {
            throw ApiException.prohibido("Esta OT no está asignada a ti.");
        }
        if (orden.getEstado() != EstadoOt.EN_EJECUCION) {
            throw ApiException.conflicto("Solo se pueden eliminar fotos mientras la OT está en ejecución.");
        }
        fotos.delete(foto);
    }

    // ------------------------------------------------------------------
    // Reglas puras (estáticas para probarlas fácilmente)
    // ------------------------------------------------------------------

    /**
     * Revisa los primeros bytes del archivo para confirmar que de verdad es una imagen
     * del tipo que dice ser (evita que se suba otro tipo de archivo con nombre de foto).
     */
    static boolean coincideConFormato(byte[] d, String contentType) {
        if (d == null || d.length < 12) {
            return false;
        }
        return switch (contentType) {
            case "image/jpeg" -> (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF;
            case "image/png" -> (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G';
            case "image/webp" -> d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                    && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P';
            default -> false;
        };
    }

    /** Deja solo el nombre del archivo (sin carpetas ni caracteres raros) y lo limita a 120 caracteres. */
    static String nombreSeguro(String original) {
        if (original == null || original.isBlank()) {
            return "foto.jpg";
        }
        String nombre = original.replace('\\', '/');
        nombre = nombre.substring(nombre.lastIndexOf('/') + 1);
        nombre = nombre.replaceAll("[^A-Za-z0-9._-]", "_");
        if (nombre.isBlank()) {
            return "foto.jpg";
        }
        return nombre.length() > 120 ? nombre.substring(nombre.length() - 120) : nombre;
    }

    private void validarTamano(long bytes) {
        if (bytes > maxBytes) {
            throw new ApiException(HttpStatus.valueOf(413),
                    "La fotografía pesa " + (bytes / 1024) + " KB y el máximo es " + (maxBytes / 1024) + " KB.");
        }
    }

    private OrdenTrabajo buscarOrden(Long id) {
        return ordenes.findById(id).orElseThrow(() -> ApiException.noEncontrado("La OT no existe."));
    }
}
