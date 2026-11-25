package com.example.acisapi.controlador;

import static com.example.acisapi.seguridad.AuthInterceptor.ATRIBUTO_USUARIO;

import com.example.acisapi.dto.FotoResumen;
import com.example.acisapi.modelo.FotoOrden;
import com.example.acisapi.modelo.Perfil;
import com.example.acisapi.modelo.TipoFoto;
import com.example.acisapi.modelo.Usuario;
import com.example.acisapi.seguridad.RequierePerfil;
import com.example.acisapi.servicio.FotoService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Fotografías de las OT (HU-20 y HU-21). Las imágenes se guardan en la base de datos. */
@RestController
public class FotoController {

    private final FotoService fotos;

    public FotoController(FotoService fotos) {
        this.fotos = fotos;
    }

    /**
     * Sube una foto. Se envía como multipart/form-data con dos partes:
     * "tipo" (ANTES o DESPUES) y "archivo" (la imagen JPEG, PNG o WEBP).
     */
    @PostMapping("/api/ordenes/{id}/fotos")
    @RequierePerfil(Perfil.TECNICO)
    @ResponseStatus(HttpStatus.CREATED)
    public FotoResumen subir(@RequestAttribute(ATRIBUTO_USUARIO) Usuario usuario,
                             @PathVariable("id") Long id,
                             @RequestParam("tipo") TipoFoto tipo,
                             @RequestParam("archivo") MultipartFile archivo) {
        return fotos.subir(usuario, id, tipo, archivo);
    }

    @GetMapping("/api/ordenes/{id}/fotos")
    public List<FotoResumen> listar(@RequestAttribute(ATRIBUTO_USUARIO) Usuario usuario, @PathVariable("id") Long id) {
        return fotos.listar(usuario, id);
    }

    /** Descarga la imagen. Como el resto de la API, necesita el encabezado Authorization. */
    @GetMapping("/api/fotos/{fotoId}")
    public ResponseEntity<byte[]> descargar(@RequestAttribute(ATRIBUTO_USUARIO) Usuario usuario,
                                            @PathVariable("fotoId") Long fotoId) {
        FotoOrden foto = fotos.obtener(usuario, fotoId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(foto.getContentType()))
                .header("Cache-Control", "private, max-age=3600")
                .body(foto.getDatos());
    }

    @DeleteMapping("/api/fotos/{fotoId}")
    @RequierePerfil(Perfil.TECNICO)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@RequestAttribute(ATRIBUTO_USUARIO) Usuario usuario, @PathVariable("fotoId") Long fotoId) {
        fotos.eliminar(usuario, fotoId);
    }
}
