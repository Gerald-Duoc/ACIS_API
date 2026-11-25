package com.example.acisapi.error;

import com.example.acisapi.dto.Respuestas.ErrorRespuesta;
import java.time.LocalDateTime;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Convierte cualquier error en una respuesta JSON uniforme:
 * { "estado": 404, "error": "Not Found", "mensaje": "...", "fecha": "..." }
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorRespuesta> negocio(ApiException e) {
        return responder(e.getEstado(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorRespuesta> validacion(MethodArgumentNotValidException e) {
        String detalle = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        if (detalle.isBlank()) {
            detalle = "Los datos enviados no son válidos.";
        }
        return responder(HttpStatus.BAD_REQUEST, detalle);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorRespuesta> peticionInvalida(Exception e) {
        return responder(HttpStatus.BAD_REQUEST, "La petición no es válida: revisa el formato de los datos enviados.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorRespuesta> archivoGrande(MaxUploadSizeExceededException e) {
        return responder(HttpStatus.valueOf(413), "El archivo es demasiado grande. Reduce el tamaño de la foto.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorRespuesta> otro(Exception e) {
        // Errores de Spring que ya traen su código HTTP (404, 405, 415, etc.)
        if (e instanceof ErrorResponse er) {
            HttpStatus estado = HttpStatus.valueOf(er.getStatusCode().value());
            return responder(estado, estado.getReasonPhrase());
        }
        LOG.error("Error inesperado", e);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado en el servidor.");
    }

    private ResponseEntity<ErrorRespuesta> responder(HttpStatus estado, String mensaje) {
        ErrorRespuesta cuerpo = new ErrorRespuesta(estado.value(), estado.getReasonPhrase(), mensaje, LocalDateTime.now());
        return ResponseEntity.status(estado).body(cuerpo);
    }
}
