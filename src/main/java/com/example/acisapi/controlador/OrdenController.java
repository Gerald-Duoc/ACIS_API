package com.example.acisapi.controlador;

import static com.example.acisapi.seguridad.AuthInterceptor.ATRIBUTO_USUARIO;

import com.example.acisapi.dto.Peticiones.AsignarRequest;
import com.example.acisapi.dto.Peticiones.CheckInRequest;
import com.example.acisapi.dto.Peticiones.ComentarioRequest;
import com.example.acisapi.dto.Peticiones.MotivoRequest;
import com.example.acisapi.dto.Peticiones.OrdenRequest;
import com.example.acisapi.dto.Peticiones.RegistroRequest;
import com.example.acisapi.dto.Peticiones.ReprogramarRequest;
import com.example.acisapi.dto.Peticiones.SolicitudRequest;
import com.example.acisapi.dto.Respuestas.ClimaDto;
import com.example.acisapi.dto.Respuestas.OrdenDto;
import com.example.acisapi.modelo.EstadoOt;
import com.example.acisapi.modelo.Perfil;
import com.example.acisapi.modelo.Usuario;
import com.example.acisapi.seguridad.RequierePerfil;
import com.example.acisapi.servicio.OrdenService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Órdenes de trabajo: planificación, ejecución en terreno y conformidad del cliente. */
@RestController
@RequestMapping("/api/ordenes")
public class OrdenController {

    private final OrdenService ordenes;

    public OrdenController(OrdenService ordenes) {
        this.ordenes = ordenes;
    }

    // ------------------ Consultas (todos los perfiles, cada uno ve lo suyo) ------------------

    @GetMapping
    public List<OrdenDto> listar(
            @RequestAttribute(ATRIBUTO_USUARIO) Usuario usuario,
            @RequestParam(name = "estado", required = false) EstadoOt estado,
            @RequestParam(name = "tecnicoId", required = false) Long tecnicoId,
            @RequestParam(name = "clienteId", required = false) Long clienteId,
            @RequestParam(name = "fecha", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ordenes.listar(usuario, estado, tecnicoId, clienteId, fecha);
    }

    @GetMapping("/{id}")
    public OrdenDto obtener(@RequestAttribute(ATRIBUTO_USUARIO) Usuario usuario, @PathVariable("id") Long id) {
        return ordenes.obtener(usuario, id);
    }

    @GetMapping("/{id}/clima")
    public ClimaDto clima(@RequestAttribute(ATRIBUTO_USUARIO) Usuario usuario, @PathVariable("id") Long id) {
        return ordenes.climaDeOrden(usuario, id);
    }

    // ------------------ Planificador ------------------

    @PostMapping
    @RequierePerfil(Perfil.PLANIFICADOR)
    @ResponseStatus(HttpStatus.CREATED)
    public OrdenDto crear(@Valid @RequestBody OrdenRequest peticion) {
        return ordenes.crear(peticion);
    }

    @PutMapping("/{id}/asignacion")
    @RequierePerfil(Perfil.PLANIFICADOR)
    public OrdenDto asignar(@PathVariable("id") Long id, @Valid @RequestBody AsignarRequest peticion) {
        return ordenes.asignar(id, peticion.tecnicoId());
    }

    @PutMapping("/{id}/fecha")
    @RequierePerfil(Perfil.PLANIFICADOR)
    public OrdenDto reprogramar(@PathVariable("id") Long id, @Valid @RequestBody ReprogramarRequest peticion) {
        return ordenes.reprogramar(id, peticion.fecha());
    }

    // ------------------ Técnico en terreno ------------------

    @PostMapping("/{id}/aceptar")
    @RequierePerfil(Perfil.TECNICO)
    public OrdenDto aceptar(@RequestAttribute(ATRIBUTO_USUARIO) Usuario usuario, @PathVariable("id") Long id) {
        return ordenes.aceptar(usuario, id);
    }

    @PostMapping("/{id}/rechazo")
    @RequierePerfil(Perfil.TECNICO)
    public OrdenDto rechazar(@RequestAttribute(ATRIBUTO_USUARIO) Usuario usuario, @PathVariable("id") Long id,
                             @Valid @RequestBody MotivoRequest peticion) {
        return ordenes.rechazar(usuario, id, peticion.motivo());
    }

    @PostMapping("/{id}/check-in")
    @RequierePerfil(Perfil.TECNICO)
    public OrdenDto checkIn(@RequestAttribute(ATRIBUTO_USUARIO) Usuario usuario, @PathVariable("id") Long id,
                            @Valid @RequestBody CheckInRequest peticion) {
        return ordenes.checkIn(usuario, id, peticion.ubicacionAutorizada());
    }

    @PostMapping("/{id}/check-out")
    @RequierePerfil(Perfil.TECNICO)
    public OrdenDto checkOut(@RequestAttribute(ATRIBUTO_USUARIO) Usuario usuario, @PathVariable("id") Long id) {
        return ordenes.checkOut(usuario, id);
    }

    @PutMapping("/{id}/registro")
    @RequierePerfil(Perfil.TECNICO)
    public OrdenDto guardarRegistro(@RequestAttribute(ATRIBUTO_USUARIO) Usuario usuario, @PathVariable("id") Long id,
                                    @RequestBody RegistroRequest peticion) {
        return ordenes.guardarRegistro(usuario, id, peticion);
    }

    @PostMapping("/{id}/imprevisto")
    @RequierePerfil(Perfil.TECNICO)
    public OrdenDto imprevisto(@RequestAttribute(ATRIBUTO_USUARIO) Usuario usuario, @PathVariable("id") Long id,
                               @Valid @RequestBody MotivoRequest peticion) {
        return ordenes.imprevisto(usuario, id, peticion.motivo());
    }

    @PostMapping("/{id}/cierre")
    @RequierePerfil(Perfil.TECNICO)
    public OrdenDto cerrar(@RequestAttribute(ATRIBUTO_USUARIO) Usuario usuario, @PathVariable("id") Long id) {
        return ordenes.cerrar(usuario, id);
    }

    // ------------------ Cliente ------------------

    @PostMapping("/solicitud")
    @RequierePerfil(Perfil.CLIENTE)
    @ResponseStatus(HttpStatus.CREATED)
    public OrdenDto solicitar(@RequestAttribute(ATRIBUTO_USUARIO) Usuario usuario,
                              @Valid @RequestBody SolicitudRequest peticion) {
        return ordenes.solicitar(usuario, peticion);
    }

    @PostMapping("/{id}/aprobacion")
    @RequierePerfil(Perfil.CLIENTE)
    public OrdenDto aprobar(@RequestAttribute(ATRIBUTO_USUARIO) Usuario usuario, @PathVariable("id") Long id) {
        return ordenes.aprobar(usuario, id);
    }

    @PostMapping("/{id}/observacion")
    @RequierePerfil(Perfil.CLIENTE)
    public OrdenDto observar(@RequestAttribute(ATRIBUTO_USUARIO) Usuario usuario, @PathVariable("id") Long id,
                             @Valid @RequestBody ComentarioRequest peticion) {
        return ordenes.observar(usuario, id, peticion.comentario());
    }
}
