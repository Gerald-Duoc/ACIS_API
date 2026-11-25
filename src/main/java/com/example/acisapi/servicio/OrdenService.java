package com.example.acisapi.servicio;

import com.example.acisapi.dto.Peticiones.OrdenRequest;
import com.example.acisapi.dto.Peticiones.RegistroRequest;
import com.example.acisapi.dto.Peticiones.SolicitudRequest;
import com.example.acisapi.dto.Respuestas.ClimaDto;
import com.example.acisapi.dto.Respuestas.OrdenDto;
import com.example.acisapi.error.ApiException;
import com.example.acisapi.modelo.Conformidad;
import com.example.acisapi.modelo.Equipo;
import com.example.acisapi.modelo.EstadoOt;
import com.example.acisapi.modelo.OrdenTrabajo;
import com.example.acisapi.modelo.Perfil;
import com.example.acisapi.modelo.Prioridad;
import com.example.acisapi.modelo.TipoFoto;
import com.example.acisapi.modelo.Usuario;
import com.example.acisapi.repositorio.EquipoRepository;
import com.example.acisapi.repositorio.FotoRepository;
import com.example.acisapi.repositorio.OrdenRepository;
import com.example.acisapi.repositorio.UsuarioRepository;
import com.example.acisapi.seguridad.Permisos;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas de negocio de las órdenes de trabajo. Cada método corresponde a una o más historias
 * de usuario (HU) y devuelve un error 4xx claro cuando no se cumple una regla.
 */
@Service
public class OrdenService {

    private final OrdenRepository ordenes;
    private final EquipoRepository equipos;
    private final UsuarioRepository usuarios;
    private final FotoRepository fotos;
    private final ClimaService clima;

    public OrdenService(OrdenRepository ordenes,
                        EquipoRepository equipos,
                        UsuarioRepository usuarios,
                        FotoRepository fotos,
                        ClimaService clima) {
        this.ordenes = ordenes;
        this.equipos = equipos;
        this.usuarios = usuarios;
        this.fotos = fotos;
        this.clima = clima;
    }

    // ------------------------------------------------------------------
    // Consultas (HU-11, HU-12, HU-13, HU-14, HU-41)
    // ------------------------------------------------------------------

    /** Lista las OT que el usuario puede ver, con filtros opcionales (HU-12). */
    @Transactional(readOnly = true)
    public List<OrdenDto> listar(Usuario usuario, EstadoOt estado, Long tecnicoId, Long clienteId, LocalDate fecha) {
        return ordenes.findAllByOrderByIdDesc().stream()
                .filter(o -> Permisos.puedeVer(usuario, o))
                .filter(o -> estado == null || o.getEstado() == estado)
                .filter(o -> tecnicoId == null || (o.getTecnico() != null && o.getTecnico().getId().equals(tecnicoId)))
                .filter(o -> clienteId == null || o.getEquipo().getLocal().getCliente().getId().equals(clienteId))
                .filter(o -> fecha == null || fecha.equals(o.getFecha()))
                .map(this::aDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrdenDto obtener(Usuario usuario, Long id) {
        return aDto(visible(usuario, id));
    }

    /** HU-29, HU-30: clima real del local de la OT en su fecha. */
    @Transactional(readOnly = true)
    public ClimaDto climaDeOrden(Usuario usuario, Long id) {
        OrdenTrabajo o = visible(usuario, id);
        if (o.getFecha() == null) {
            throw ApiException.conflicto("La OT todavía no tiene fecha agendada.");
        }
        return clima.consultar(o.getEquipo().getLocal().getLatitud(), o.getEquipo().getLocal().getLongitud(),
                o.getFecha(), o.getEquipo().isExterior());
    }

    // ------------------------------------------------------------------
    // Planificador (HU-06 a HU-10)
    // ------------------------------------------------------------------

    /** HU-06, HU-07, HU-08: crea una OT; si trae técnico queda Asignada, si no queda Creada. */
    @Transactional
    public OrdenDto crear(OrdenRequest r) {
        Equipo equipo = equipos.findById(r.equipoId())
                .orElseThrow(() -> ApiException.noEncontrado("El equipo no existe."));
        validarFechaNoPasada(r.fecha());
        Usuario tecnico = r.tecnicoId() == null ? null : buscarTecnico(r.tecnicoId());

        OrdenTrabajo o = nueva(equipo, r.tipo(), r.prioridad(), r.fecha(), r.descripcion().trim());
        o.setTecnico(tecnico);
        o.setEstado(tecnico != null ? EstadoOt.ASIGNADA : EstadoOt.CREADA);
        return aDto(ordenes.save(o));
    }

    /** HU-08 y HU-09: asigna o reasigna una OT a un técnico. */
    @Transactional
    public OrdenDto asignar(Long id, Long tecnicoId) {
        OrdenTrabajo o = buscar(id);
        exigirEstado(o, "asignar", EstadoOt.CREADA, EstadoOt.PENDIENTE_ASIGNACION, EstadoOt.ASIGNADA,
                EstadoOt.ACEPTADA, EstadoOt.PENDIENTE_IMPREVISTO);
        o.setTecnico(buscarTecnico(tecnicoId));
        o.setEstado(EstadoOt.ASIGNADA);
        o.setObservacion(null);
        return aDto(o);
    }

    /** HU-10: cambia la fecha de una OT. */
    @Transactional
    public OrdenDto reprogramar(Long id, LocalDate fecha) {
        OrdenTrabajo o = buscar(id);
        exigirEstado(o, "reprogramar", EstadoOt.CREADA, EstadoOt.PENDIENTE_ASIGNACION, EstadoOt.ASIGNADA,
                EstadoOt.ACEPTADA, EstadoOt.PENDIENTE_IMPREVISTO);
        validarFechaNoPasada(fecha);
        o.setFecha(fecha);
        return aDto(o);
    }

    // ------------------------------------------------------------------
    // Técnico en terreno (HU-15 a HU-25)
    // ------------------------------------------------------------------

    /** HU-15: el técnico acepta la OT. */
    @Transactional
    public OrdenDto aceptar(Usuario tecnico, Long id) {
        OrdenTrabajo o = delTecnico(tecnico, id);
        exigirEstado(o, "aceptar", EstadoOt.ASIGNADA);
        o.setEstado(EstadoOt.ACEPTADA);
        return aDto(o);
    }

    /** HU-16: rechazar exige un motivo; la OT vuelve a Pendiente de asignación. */
    @Transactional
    public OrdenDto rechazar(Usuario tecnico, Long id, String motivo) {
        OrdenTrabajo o = delTecnico(tecnico, id);
        exigirEstado(o, "rechazar", EstadoOt.ASIGNADA);
        exigirTexto(motivo, "El motivo del rechazo es obligatorio.");
        o.setObservacion("Rechazada por " + tecnico.getNombre() + ": " + motivo.trim());
        o.setTecnico(null);
        o.setEstado(EstadoOt.PENDIENTE_ASIGNACION);
        return aDto(o);
    }

    /** HU-05 y HU-17: check-in. Solo si el técnico autorizó el uso de su ubicación. */
    @Transactional
    public OrdenDto checkIn(Usuario tecnico, Long id, boolean ubicacionAutorizada) {
        OrdenTrabajo o = delTecnico(tecnico, id);
        exigirEstado(o, "hacer check-in en", EstadoOt.ACEPTADA);
        if (!ubicacionAutorizada) {
            throw ApiException.invalido("Debes autorizar el uso de tu ubicación para hacer el check-in.");
        }
        o.setUbicacionAutorizada(true);
        o.setCheckIn(LocalDateTime.now());
        o.setEstado(EstadoOt.EN_EJECUCION);
        return aDto(o);
    }

    /** HU-24: check-out y tiempo de atención (en minutos, mínimo 1). */
    @Transactional
    public OrdenDto checkOut(Usuario tecnico, Long id) {
        OrdenTrabajo o = delTecnico(tecnico, id);
        exigirEstado(o, "hacer check-out en", EstadoOt.EN_EJECUCION);
        if (o.getCheckIn() == null) {
            throw ApiException.conflicto("Primero debes registrar el check-in.");
        }
        if (o.getCheckOut() != null) {
            throw ApiException.conflicto("Ya registraste el check-out de esta OT.");
        }
        LocalDateTime ahora = LocalDateTime.now();
        o.setCheckOut(ahora);
        o.setTiempoRealMin(minutosDeAtencion(o.getCheckIn(), ahora));
        return aDto(o);
    }

    /** HU-18, HU-19, HU-22: guarda el diagnóstico, las actividades y los materiales. */
    @Transactional
    public OrdenDto guardarRegistro(Usuario tecnico, Long id, RegistroRequest r) {
        OrdenTrabajo o = delTecnico(tecnico, id);
        exigirEstado(o, "registrar datos en", EstadoOt.EN_EJECUCION);
        o.setDiagnostico(r.diagnostico() == null ? "" : r.diagnostico().trim());
        reemplazar(o.getActividades(), r.actividades());
        reemplazar(o.getMateriales(), r.materiales());
        return aDto(o);
    }

    /** HU-23: reporta un imprevisto; la OT queda Pendiente por imprevisto. */
    @Transactional
    public OrdenDto imprevisto(Usuario tecnico, Long id, String motivo) {
        OrdenTrabajo o = delTecnico(tecnico, id);
        exigirEstado(o, "reportar un imprevisto en", EstadoOt.ACEPTADA, EstadoOt.EN_EJECUCION);
        exigirTexto(motivo, "El motivo del imprevisto es obligatorio.");
        o.setObservacion("Imprevisto: " + motivo.trim());
        o.setEstado(EstadoOt.PENDIENTE_IMPREVISTO);
        return aDto(o);
    }

    /** HU-25: cierra la OT si el registro está completo. */
    @Transactional
    public OrdenDto cerrar(Usuario tecnico, Long id) {
        OrdenTrabajo o = delTecnico(tecnico, id);
        exigirEstado(o, "cerrar", EstadoOt.EN_EJECUCION);
        String faltante = primerRequisitoFaltante(o);
        if (faltante != null) {
            throw ApiException.invalido(faltante);
        }
        o.setEstado(EstadoOt.CERRADA);
        o.setConformidad(Conformidad.SIN_REVISAR);
        o.setObservacion(null);
        return aDto(o);
    }

    // ------------------------------------------------------------------
    // Cliente (HU-40, HU-42, HU-43)
    // ------------------------------------------------------------------

    /** HU-40: el cliente solicita una mantención de un equipo de su local. */
    @Transactional
    public OrdenDto solicitar(Usuario cliente, SolicitudRequest r) {
        Equipo equipo = equipos.findById(r.equipoId())
                .orElseThrow(() -> ApiException.noEncontrado("El equipo no existe."));
        if (cliente.getLocal() == null || !equipo.getLocal().getId().equals(cliente.getLocal().getId())) {
            throw ApiException.prohibido("Solo puedes solicitar mantención de equipos de tu local.");
        }
        OrdenTrabajo o = nueva(equipo, "Correctiva", Prioridad.MEDIA, null, r.descripcion().trim());
        o.setEstado(EstadoOt.CREADA);
        o.setObservacion("Solicitada por el cliente");
        return aDto(ordenes.save(o));
    }

    /** HU-42: el cliente aprueba el trabajo; queda registrada la fecha y la hora. */
    @Transactional
    public OrdenDto aprobar(Usuario cliente, Long id) {
        OrdenTrabajo o = pendienteDeConformidad(cliente, id);
        o.setConformidad(Conformidad.APROBADA);
        o.setFechaConformidad(LocalDateTime.now());
        return aDto(o);
    }

    /** HU-43: el cliente rechaza el trabajo con un comentario obligatorio. */
    @Transactional
    public OrdenDto observar(Usuario cliente, Long id, String comentario) {
        OrdenTrabajo o = pendienteDeConformidad(cliente, id);
        exigirTexto(comentario, "El comentario es obligatorio.");
        o.setConformidad(Conformidad.OBSERVADA);
        o.setFechaConformidad(LocalDateTime.now());
        o.setComentarioCliente(comentario.trim());
        return aDto(o);
    }

    // ------------------------------------------------------------------
    // Reglas puras (públicas y estáticas para poder probarlas sin base de datos)
    // ------------------------------------------------------------------

    /** Minutos entre el check-in y el check-out. Nunca menos de 1. */
    public static int minutosDeAtencion(LocalDateTime checkIn, LocalDateTime checkOut) {
        long minutos = Duration.between(checkIn, checkOut).toMinutes();
        return (int) Math.max(1, minutos);
    }

    /** true si el tiempo real cumple el SLA (que está en horas). */
    public static boolean cumpleSla(int tiempoRealMin, int slaHoras) {
        return tiempoRealMin <= slaHoras * 60;
    }

    // ------------------------------------------------------------------
    // Apoyo interno
    // ------------------------------------------------------------------

    /**
     * Revisa que el registro de la visita esté completo antes de cerrar.
     * Es el mismo criterio que aplica la app (por eso incluye materiales y fotos).
     * Devuelve el mensaje del primer requisito que falta, o null si está todo.
     */
    private String primerRequisitoFaltante(OrdenTrabajo o) {
        if (!o.isUbicacionAutorizada()) {
            return "Debes autorizar el uso de tu ubicación (paso 1).";
        }
        if (o.getCheckIn() == null || o.getCheckOut() == null) {
            return "Debes registrar el check-in y el check-out (paso 1).";
        }
        if (o.getDiagnostico() == null || o.getDiagnostico().isBlank()) {
            return "El diagnóstico es obligatorio (paso 2).";
        }
        if (o.getActividades().isEmpty()) {
            return "Agrega al menos una actividad realizada (paso 3).";
        }
        if (o.getMateriales().isEmpty()) {
            return "Agrega al menos un material utilizado (paso 3).";
        }
        boolean hayAntes = fotos.countByOrdenIdAndTipo(o.getId(), TipoFoto.ANTES) > 0;
        boolean hayDespues = fotos.countByOrdenIdAndTipo(o.getId(), TipoFoto.DESPUES) > 0;
        if (!hayAntes || !hayDespues) {
            return "Debes agregar al menos una foto antes y una después (paso 3).";
        }
        return null;
    }

    private OrdenTrabajo nueva(Equipo equipo, String tipo, Prioridad prioridad, LocalDate fecha, String descripcion) {
        OrdenTrabajo o = new OrdenTrabajo();
        o.setNumero(ordenes.maxNumero() + 1);
        o.setEquipo(equipo);
        o.setTipo(tipo);
        o.setPrioridad(prioridad);
        o.setFecha(fecha);
        o.setDescripcion(descripcion);
        o.setSlaHoras(equipo.getLocal().getCliente().getSlaHoras());
        o.setCreadaEn(LocalDateTime.now());
        return o;
    }

    private OrdenTrabajo buscar(Long id) {
        return ordenes.findById(id).orElseThrow(() -> ApiException.noEncontrado("La OT no existe."));
    }

    /** Busca la OT y confirma que el usuario tenga permiso para verla (si no, responde como si no existiera). */
    private OrdenTrabajo visible(Usuario usuario, Long id) {
        OrdenTrabajo o = buscar(id);
        if (!Permisos.puedeVer(usuario, o)) {
            throw ApiException.noEncontrado("La OT no existe.");
        }
        return o;
    }

    private OrdenTrabajo delTecnico(Usuario tecnico, Long id) {
        OrdenTrabajo o = buscar(id);
        if (!Permisos.esTecnicoAsignado(tecnico, o)) {
            throw ApiException.prohibido("Esta OT no está asignada a ti.");
        }
        return o;
    }

    private OrdenTrabajo pendienteDeConformidad(Usuario cliente, Long id) {
        OrdenTrabajo o = visible(cliente, id);
        exigirEstado(o, "dar conformidad a", EstadoOt.CERRADA);
        if (o.getConformidad() != Conformidad.SIN_REVISAR) {
            throw ApiException.conflicto("Ya respondiste esta OT.");
        }
        return o;
    }

    private Usuario buscarTecnico(Long id) {
        Usuario u = usuarios.findById(id).orElseThrow(() -> ApiException.noEncontrado("El técnico no existe."));
        if (u.getPerfil() != Perfil.TECNICO) {
            throw ApiException.invalido("El usuario elegido no es un técnico.");
        }
        return u;
    }

    private static void exigirEstado(OrdenTrabajo o, String accion, EstadoOt... permitidos) {
        for (EstadoOt estado : permitidos) {
            if (o.getEstado() == estado) {
                return;
            }
        }
        throw ApiException.conflicto("No se puede " + accion + " una OT en estado \"" + o.getEstado().etiqueta() + "\".");
    }

    private static void exigirTexto(String texto, String mensaje) {
        if (texto == null || texto.isBlank()) {
            throw ApiException.invalido(mensaje);
        }
    }

    private static void validarFechaNoPasada(LocalDate fecha) {
        if (fecha.isBefore(LocalDate.now())) {
            throw ApiException.invalido("La fecha no puede ser anterior a hoy.");
        }
    }

    /** Reemplaza el contenido de una lista guardada, quitando textos vacíos. */
    private static void reemplazar(List<String> destino, List<String> nuevos) {
        destino.clear();
        if (nuevos != null) {
            nuevos.stream()
                    .filter(t -> t != null && !t.isBlank())
                    .map(String::trim)
                    .forEach(destino::add);
        }
    }

    private OrdenDto aDto(OrdenTrabajo o) {
        Equipo e = o.getEquipo();
        Usuario t = o.getTecnico();
        return new OrdenDto(
                o.getId(),
                o.getCodigo(),
                o.getEstado(),
                o.getPrioridad(),
                o.getTipo(),
                o.getFecha(),
                o.getDescripcion(),
                o.getObservacion(),
                o.getSlaHoras(),
                o.getTiempoRealMin(),
                e.getLocal().getCliente().getId(),
                e.getLocal().getCliente().getNombre(),
                e.getLocal().getId(),
                e.getLocal().getNombre(),
                e.getLocal().getDireccion(),
                e.getId(),
                e.getNombre(),
                e.isExterior(),
                e.getUbicacion(),
                e.getUltimaMantencion(),
                t == null ? null : t.getId(),
                t == null ? null : t.getNombre(),
                o.isUbicacionAutorizada(),
                o.getCheckIn(),
                o.getCheckOut(),
                o.getDiagnostico(),
                List.copyOf(o.getActividades()),
                List.copyOf(o.getMateriales()),
                o.getId() == null ? 0 : fotos.countByOrdenIdAndTipo(o.getId(), TipoFoto.ANTES),
                o.getId() == null ? 0 : fotos.countByOrdenIdAndTipo(o.getId(), TipoFoto.DESPUES),
                o.getConformidad(),
                o.getFechaConformidad(),
                o.getComentarioCliente());
    }
}
