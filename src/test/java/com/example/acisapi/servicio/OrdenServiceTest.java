package com.example.acisapi.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.acisapi.Fabrica;
import com.example.acisapi.dto.Peticiones.OrdenRequest;
import com.example.acisapi.dto.Peticiones.RegistroRequest;
import com.example.acisapi.dto.Peticiones.SolicitudRequest;
import com.example.acisapi.dto.Respuestas.OrdenDto;
import com.example.acisapi.error.ApiException;
import com.example.acisapi.modelo.Cliente;
import com.example.acisapi.modelo.Conformidad;
import com.example.acisapi.modelo.Equipo;
import com.example.acisapi.modelo.EstadoOt;
import com.example.acisapi.modelo.Local;
import com.example.acisapi.modelo.OrdenTrabajo;
import com.example.acisapi.modelo.Perfil;
import com.example.acisapi.modelo.Prioridad;
import com.example.acisapi.modelo.TipoFoto;
import com.example.acisapi.modelo.Usuario;
import com.example.acisapi.repositorio.EquipoRepository;
import com.example.acisapi.repositorio.FotoRepository;
import com.example.acisapi.repositorio.OrdenRepository;
import com.example.acisapi.repositorio.UsuarioRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Reglas de negocio de las órdenes de trabajo (HU-06 a HU-25, HU-40 a HU-43).
 * Usa repositorios simulados (Mockito): no necesita base de datos.
 */
class OrdenServiceTest {

    private final OrdenRepository ordenes = mock(OrdenRepository.class);
    private final EquipoRepository equipos = mock(EquipoRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final FotoRepository fotos = mock(FotoRepository.class);
    private final ClimaService clima = mock(ClimaService.class);
    private final OrdenService servicio = new OrdenService(ordenes, equipos, usuarios, fotos, clima);

    private final Cliente cordillera = Fabrica.cliente(1, "Supermercado Cordillera", 4);
    private final Local independencia = Fabrica.local(10, cordillera, "Sucursal Independencia");
    private final Local maipu = Fabrica.local(11, cordillera, "Sucursal Maipú");
    private final Equipo rooftop = Fabrica.equipo(100, independencia, "Condensadora rooftop 1", true);
    private final Equipo camaraMaipu = Fabrica.equipo(101, maipu, "Cámara frigorífica 2", false);

    private final Usuario carlos = Fabrica.usuario(1, "Carlos Muñoz", "tecnico@acis.cl", Perfil.TECNICO);
    private final Usuario javiera = Fabrica.usuario(2, "Javiera Rojas", "jrojas@acis.cl", Perfil.TECNICO);
    private final Usuario planificador = Fabrica.usuario(3, "Planificador", "planificador@acis.cl", Perfil.PLANIFICADOR);
    private final Usuario clienteIndependencia = Fabrica.usuario(4, "Ana Pérez", "cliente@acis.cl", Perfil.CLIENTE);

    @BeforeEach
    void preparar() {
        clienteIndependencia.asociarALocal(independencia);
        when(ordenes.save(any(OrdenTrabajo.class))).thenAnswer(i -> i.getArgument(0));
        when(ordenes.maxNumero()).thenReturn(1045);
        when(equipos.findById(100L)).thenReturn(Optional.of(rooftop));
        when(equipos.findById(101L)).thenReturn(Optional.of(camaraMaipu));
        when(usuarios.findById(1L)).thenReturn(Optional.of(carlos));
        when(usuarios.findById(2L)).thenReturn(Optional.of(javiera));
        when(usuarios.findById(3L)).thenReturn(Optional.of(planificador));
    }

    /** Deja una OT guardada con el id 500 para que el servicio la encuentre. */
    private OrdenTrabajo ot(EstadoOt estado, Usuario tecnico) {
        OrdenTrabajo o = Fabrica.orden(500, 1041, rooftop, estado, tecnico);
        when(ordenes.findById(500L)).thenReturn(Optional.of(o));
        return o;
    }

    private static void esperarError(HttpStatus esperado, Runnable accion) {
        ApiException e = assertThrows(ApiException.class, accion::run);
        assertEquals(esperado, e.getEstado());
    }

    // ------------------------------------------------------------------
    // HU-06, HU-07, HU-08: crear una OT
    // ------------------------------------------------------------------

    private OrdenRequest peticion(Long tecnicoId, LocalDate fecha) {
        return new OrdenRequest(100L, "Correctiva", Prioridad.ALTA, fecha, tecnicoId, "  No enfría  ");
    }

    @Test
    void HU06_crear_una_OT_sin_tecnico_la_deja_en_estado_Creada() {
        OrdenDto dto = servicio.crear(peticion(null, LocalDate.now().plusDays(1)));

        assertEquals(EstadoOt.CREADA, dto.estado());
        assertNull(dto.tecnicoId());
        assertEquals("OT-1046", dto.codigo());
        assertEquals("No enfría", dto.descripcion());
    }

    @Test
    void HU06_la_OT_hereda_el_SLA_del_cliente() {
        OrdenDto dto = servicio.crear(peticion(null, LocalDate.now()));

        assertEquals(4, dto.slaHoras());
    }

    @Test
    void HU07_la_prioridad_elegida_queda_guardada() {
        OrdenDto dto = servicio.crear(peticion(null, LocalDate.now()));

        assertEquals(Prioridad.ALTA, dto.prioridad());
    }

    @Test
    void HU08_crear_una_OT_con_tecnico_la_deja_Asignada_a_ese_tecnico() {
        OrdenDto dto = servicio.crear(peticion(1L, LocalDate.now()));

        assertEquals(EstadoOt.ASIGNADA, dto.estado());
        assertEquals("Carlos Muñoz", dto.tecnicoNombre());
    }

    @Test
    void HU06_no_se_puede_crear_una_OT_con_fecha_pasada() {
        esperarError(HttpStatus.BAD_REQUEST, () -> servicio.crear(peticion(null, LocalDate.now().minusDays(1))));
    }

    @Test
    void HU06_el_equipo_debe_existir() {
        when(equipos.findById(999L)).thenReturn(Optional.empty());
        OrdenRequest r = new OrdenRequest(999L, "Correctiva", Prioridad.MEDIA, LocalDate.now(), null, "x");

        esperarError(HttpStatus.NOT_FOUND, () -> servicio.crear(r));
    }

    @Test
    void HU08_solo_se_puede_asignar_a_un_usuario_con_perfil_tecnico() {
        esperarError(HttpStatus.BAD_REQUEST, () -> servicio.crear(peticion(3L, LocalDate.now())));
    }

    // ------------------------------------------------------------------
    // HU-08, HU-09, HU-10: asignar, reasignar y reprogramar
    // ------------------------------------------------------------------

    @Test
    void HU08_asignar_una_OT_Creada_la_deja_Asignada() {
        ot(EstadoOt.CREADA, null);

        OrdenDto dto = servicio.asignar(500L, 1L);

        assertEquals(EstadoOt.ASIGNADA, dto.estado());
        assertEquals("Carlos Muñoz", dto.tecnicoNombre());
    }

    @Test
    void HU09_reasignar_cambia_el_tecnico_y_la_deja_Asignada_de_nuevo() {
        OrdenTrabajo o = ot(EstadoOt.ACEPTADA, carlos);

        OrdenDto dto = servicio.asignar(500L, 2L);

        assertEquals("Javiera Rojas", dto.tecnicoNombre());
        assertEquals(EstadoOt.ASIGNADA, dto.estado());
        assertEquals(javiera, o.getTecnico());
    }

    @Test
    void HU09_no_se_puede_reasignar_una_OT_en_ejecucion_ni_cerrada() {
        ot(EstadoOt.EN_EJECUCION, carlos);
        esperarError(HttpStatus.CONFLICT, () -> servicio.asignar(500L, 2L));

        ot(EstadoOt.CERRADA, carlos);
        esperarError(HttpStatus.CONFLICT, () -> servicio.asignar(500L, 2L));
    }

    @Test
    void HU10_reprogramar_guarda_la_nueva_fecha() {
        ot(EstadoOt.ASIGNADA, carlos);
        LocalDate nueva = LocalDate.now().plusDays(5);

        OrdenDto dto = servicio.reprogramar(500L, nueva);

        assertEquals(nueva, dto.fecha());
    }

    @Test
    void HU10_no_se_puede_reprogramar_a_una_fecha_pasada() {
        ot(EstadoOt.ASIGNADA, carlos);

        esperarError(HttpStatus.BAD_REQUEST, () -> servicio.reprogramar(500L, LocalDate.now().minusDays(1)));
    }

    @Test
    void HU10_no_se_puede_reprogramar_una_OT_cerrada() {
        ot(EstadoOt.CERRADA, carlos);

        esperarError(HttpStatus.CONFLICT, () -> servicio.reprogramar(500L, LocalDate.now().plusDays(1)));
    }

    // ------------------------------------------------------------------
    // HU-11, HU-12, HU-13, HU-41: qué ve cada perfil y los filtros
    // ------------------------------------------------------------------

    private void variasOt() {
        OrdenTrabajo deCarlos = Fabrica.orden(501, 1041, rooftop, EstadoOt.ASIGNADA, carlos);
        OrdenTrabajo deJaviera = Fabrica.orden(502, 1042, rooftop, EstadoOt.CERRADA, javiera);
        OrdenTrabajo enMaipu = Fabrica.orden(503, 1043, camaraMaipu, EstadoOt.CREADA, null);
        when(ordenes.findAllByOrderByIdDesc()).thenReturn(List.of(enMaipu, deJaviera, deCarlos));
    }

    @Test
    void HU11_el_planificador_ve_todas_las_OT() {
        variasOt();

        assertEquals(3, servicio.listar(planificador, null, null, null, null).size());
    }

    @Test
    void HU13_el_tecnico_ve_solo_las_OT_asignadas_a_el() {
        variasOt();

        List<OrdenDto> lista = servicio.listar(carlos, null, null, null, null);

        assertEquals(1, lista.size());
        assertEquals("Carlos Muñoz", lista.get(0).tecnicoNombre());
    }

    @Test
    void HU41_el_cliente_ve_solo_las_OT_de_su_local() {
        variasOt();

        List<OrdenDto> lista = servicio.listar(clienteIndependencia, null, null, null, null);

        assertEquals(2, lista.size());
        assertTrue(lista.stream().allMatch(o -> o.localNombre().equals("Sucursal Independencia")));
    }

    @Test
    void HU12_se_puede_filtrar_por_estado() {
        variasOt();

        List<OrdenDto> lista = servicio.listar(planificador, EstadoOt.CERRADA, null, null, null);

        assertEquals(1, lista.size());
        assertEquals(EstadoOt.CERRADA, lista.get(0).estado());
    }

    @Test
    void HU12_se_puede_filtrar_por_tecnico() {
        variasOt();

        List<OrdenDto> lista = servicio.listar(planificador, null, 2L, null, null);

        assertEquals(1, lista.size());
        assertEquals("Javiera Rojas", lista.get(0).tecnicoNombre());
    }

    @Test
    void HU12_se_puede_filtrar_por_cliente() {
        variasOt();

        assertEquals(3, servicio.listar(planificador, null, null, 1L, null).size());
        assertEquals(0, servicio.listar(planificador, null, null, 99L, null).size());
    }

    @Test
    void HU12_se_puede_filtrar_por_fecha() {
        variasOt();

        assertEquals(3, servicio.listar(planificador, null, null, null, LocalDate.now().plusDays(1)).size());
        assertEquals(0, servicio.listar(planificador, null, null, null, LocalDate.now().plusDays(9)).size());
    }

    @Test
    void HU14_un_tecnico_no_puede_ver_el_detalle_de_la_OT_de_otro() {
        ot(EstadoOt.ASIGNADA, carlos);

        esperarError(HttpStatus.NOT_FOUND, () -> servicio.obtener(javiera, 500L));
    }

    @Test
    void HU14_el_detalle_trae_los_datos_de_la_visita() {
        ot(EstadoOt.ASIGNADA, carlos);

        OrdenDto dto = servicio.obtener(carlos, 500L);

        assertEquals("Supermercado Cordillera", dto.clienteNombre());
        assertEquals("Condensadora rooftop 1", dto.equipoNombre());
        assertTrue(dto.equipoExterior());
        assertEquals("Correctiva", dto.tipo());
    }

    // ------------------------------------------------------------------
    // HU-15, HU-16: aceptar y rechazar
    // ------------------------------------------------------------------

    @Test
    void HU15_aceptar_una_OT_la_deja_Aceptada() {
        ot(EstadoOt.ASIGNADA, carlos);

        assertEquals(EstadoOt.ACEPTADA, servicio.aceptar(carlos, 500L).estado());
    }

    @Test
    void HU15_un_tecnico_no_puede_aceptar_la_OT_de_otro() {
        ot(EstadoOt.ASIGNADA, carlos);

        esperarError(HttpStatus.FORBIDDEN, () -> servicio.aceptar(javiera, 500L));
    }

    @Test
    void HU15_solo_se_acepta_una_OT_Asignada() {
        ot(EstadoOt.ACEPTADA, carlos);

        esperarError(HttpStatus.CONFLICT, () -> servicio.aceptar(carlos, 500L));
    }

    @Test
    void HU16_rechazar_sin_motivo_no_cambia_la_OT() {
        OrdenTrabajo o = ot(EstadoOt.ASIGNADA, carlos);

        esperarError(HttpStatus.BAD_REQUEST, () -> servicio.rechazar(carlos, 500L, "   "));

        assertEquals(EstadoOt.ASIGNADA, o.getEstado());
        assertEquals(carlos, o.getTecnico());
    }

    @Test
    void HU16_rechazar_con_motivo_la_devuelve_a_Pendiente_de_asignacion() {
        OrdenTrabajo o = ot(EstadoOt.ASIGNADA, carlos);

        OrdenDto dto = servicio.rechazar(carlos, 500L, "Tengo otra visita urgente");

        assertEquals(EstadoOt.PENDIENTE_ASIGNACION, dto.estado());
        assertNull(o.getTecnico());
        assertTrue(o.getObservacion().contains("Tengo otra visita urgente"));
        assertTrue(o.getObservacion().contains("Carlos Muñoz"));
    }

    // ------------------------------------------------------------------
    // HU-05, HU-17, HU-24: check-in y check-out
    // ------------------------------------------------------------------

    @Test
    void HU05_el_check_in_exige_que_el_tecnico_autorice_su_ubicacion() {
        OrdenTrabajo o = ot(EstadoOt.ACEPTADA, carlos);

        esperarError(HttpStatus.BAD_REQUEST, () -> servicio.checkIn(carlos, 500L, false));

        assertEquals(EstadoOt.ACEPTADA, o.getEstado());
        assertNull(o.getCheckIn());
    }

    @Test
    void HU17_el_check_in_guarda_la_hora_y_deja_la_OT_En_ejecucion() {
        OrdenTrabajo o = ot(EstadoOt.ACEPTADA, carlos);

        OrdenDto dto = servicio.checkIn(carlos, 500L, true);

        assertEquals(EstadoOt.EN_EJECUCION, dto.estado());
        assertNotNull(o.getCheckIn());
        assertTrue(o.isUbicacionAutorizada());
    }

    @Test
    void HU17_solo_se_hace_check_in_en_una_OT_Aceptada() {
        ot(EstadoOt.ASIGNADA, carlos);

        esperarError(HttpStatus.CONFLICT, () -> servicio.checkIn(carlos, 500L, true));
    }

    @Test
    void HU24_el_check_out_guarda_la_hora_y_el_tiempo_de_atencion() {
        OrdenTrabajo o = ot(EstadoOt.EN_EJECUCION, carlos);
        o.setCheckIn(LocalDateTime.now().minusMinutes(90));

        OrdenDto dto = servicio.checkOut(carlos, 500L);

        assertNotNull(o.getCheckOut());
        assertEquals(90, dto.tiempoRealMin());
    }

    @Test
    void HU24_no_se_puede_hacer_check_out_sin_check_in() {
        ot(EstadoOt.EN_EJECUCION, carlos);

        esperarError(HttpStatus.CONFLICT, () -> servicio.checkOut(carlos, 500L));
    }

    @Test
    void HU24_el_check_out_no_se_puede_repetir() {
        OrdenTrabajo o = ot(EstadoOt.EN_EJECUCION, carlos);
        o.setCheckIn(LocalDateTime.now().minusMinutes(30));
        o.setCheckOut(LocalDateTime.now());

        esperarError(HttpStatus.CONFLICT, () -> servicio.checkOut(carlos, 500L));
    }

    @Test
    void HU24_el_tiempo_de_atencion_se_calcula_en_minutos_y_nunca_es_menor_a_1() {
        LocalDateTime entrada = LocalDateTime.of(2026, 11, 24, 9, 0);

        assertEquals(135, OrdenService.minutosDeAtencion(entrada, entrada.plusMinutes(135)));
        assertEquals(1, OrdenService.minutosDeAtencion(entrada, entrada.plusSeconds(20)));
        assertEquals(1, OrdenService.minutosDeAtencion(entrada, entrada));
    }

    // ------------------------------------------------------------------
    // HU-18, HU-19, HU-22: diagnóstico, actividades y materiales
    // ------------------------------------------------------------------

    @Test
    void HU18_HU19_HU22_el_registro_guarda_diagnostico_actividades_y_materiales() {
        OrdenTrabajo o = ot(EstadoOt.EN_EJECUCION, carlos);

        OrdenDto dto = servicio.guardarRegistro(carlos, 500L, new RegistroRequest(
                "  Compresor sin carga  ",
                List.of("Cambio de compresor", "  Prueba de funcionamiento  "),
                List.of("Compresor × 1")));

        assertEquals("Compresor sin carga", dto.diagnostico());
        assertEquals(List.of("Cambio de compresor", "Prueba de funcionamiento"), dto.actividades());
        assertEquals(List.of("Compresor × 1"), o.getMateriales());
    }

    @Test
    void HU19_las_actividades_vacias_se_descartan() {
        ot(EstadoOt.EN_EJECUCION, carlos);

        OrdenDto dto = servicio.guardarRegistro(carlos, 500L, new RegistroRequest("x", List.of("A", "  ", ""), null));

        assertEquals(List.of("A"), dto.actividades());
        assertTrue(dto.materiales().isEmpty());
    }

    @Test
    void HU18_guardar_de_nuevo_reemplaza_el_registro_anterior() {
        OrdenTrabajo o = ot(EstadoOt.EN_EJECUCION, carlos);
        servicio.guardarRegistro(carlos, 500L, new RegistroRequest("Uno", List.of("A", "B"), List.of("M1")));

        servicio.guardarRegistro(carlos, 500L, new RegistroRequest("Dos", List.of("C"), List.of()));

        assertEquals("Dos", o.getDiagnostico());
        assertEquals(List.of("C"), o.getActividades());
        assertTrue(o.getMateriales().isEmpty());
    }

    @Test
    void HU18_solo_se_registra_mientras_la_OT_esta_En_ejecucion() {
        ot(EstadoOt.ACEPTADA, carlos);

        esperarError(HttpStatus.CONFLICT,
                () -> servicio.guardarRegistro(carlos, 500L, new RegistroRequest("x", List.of(), List.of())));
    }

    // ------------------------------------------------------------------
    // HU-23: imprevisto
    // ------------------------------------------------------------------

    @Test
    void HU23_reportar_un_imprevisto_sin_motivo_se_rechaza() {
        OrdenTrabajo o = ot(EstadoOt.ACEPTADA, carlos);

        esperarError(HttpStatus.BAD_REQUEST, () -> servicio.imprevisto(carlos, 500L, " "));

        assertEquals(EstadoOt.ACEPTADA, o.getEstado());
    }

    @Test
    void HU23_con_un_motivo_la_OT_queda_Pendiente_por_imprevisto() {
        OrdenTrabajo o = ot(EstadoOt.EN_EJECUCION, carlos);

        OrdenDto dto = servicio.imprevisto(carlos, 500L, "Local cerrado");

        assertEquals(EstadoOt.PENDIENTE_IMPREVISTO, dto.estado());
        assertEquals("Imprevisto: Local cerrado", o.getObservacion());
    }

    // ------------------------------------------------------------------
    // HU-25: cerrar la OT
    // ------------------------------------------------------------------

    /** OT En ejecución con TODO el registro completo y fotos de antes y después. */
    private OrdenTrabajo otListaParaCerrar() {
        OrdenTrabajo o = ot(EstadoOt.EN_EJECUCION, carlos);
        o.setUbicacionAutorizada(true);
        o.setCheckIn(LocalDateTime.now().minusMinutes(60));
        o.setCheckOut(LocalDateTime.now());
        o.setDiagnostico("Compresor sin carga");
        o.getActividades().add("Cambio de compresor");
        o.getMateriales().add("Compresor × 1");
        when(fotos.countByOrdenIdAndTipo(500L, TipoFoto.ANTES)).thenReturn(1L);
        when(fotos.countByOrdenIdAndTipo(500L, TipoFoto.DESPUES)).thenReturn(1L);
        return o;
    }

    @Test
    void HU25_con_el_registro_completo_la_OT_pasa_a_Cerrada() {
        OrdenTrabajo o = otListaParaCerrar();

        OrdenDto dto = servicio.cerrar(carlos, 500L);

        assertEquals(EstadoOt.CERRADA, dto.estado());
        assertEquals(Conformidad.SIN_REVISAR, o.getConformidad());
    }

    @Test
    void HU25_sin_autorizar_la_ubicacion_no_se_puede_cerrar() {
        OrdenTrabajo o = otListaParaCerrar();
        o.setUbicacionAutorizada(false);

        esperarError(HttpStatus.BAD_REQUEST, () -> servicio.cerrar(carlos, 500L));
        assertEquals(EstadoOt.EN_EJECUCION, o.getEstado());
    }

    @Test
    void HU25_sin_check_out_no_se_puede_cerrar() {
        OrdenTrabajo o = otListaParaCerrar();
        o.setCheckOut(null);

        ApiException e = assertThrows(ApiException.class, () -> servicio.cerrar(carlos, 500L));

        assertEquals("Debes registrar el check-in y el check-out (paso 1).", e.getMessage());
    }

    @Test
    void HU25_sin_diagnostico_no_se_puede_cerrar() {
        OrdenTrabajo o = otListaParaCerrar();
        o.setDiagnostico("   ");

        ApiException e = assertThrows(ApiException.class, () -> servicio.cerrar(carlos, 500L));

        assertEquals("El diagnóstico es obligatorio (paso 2).", e.getMessage());
        assertEquals(EstadoOt.EN_EJECUCION, o.getEstado());
    }

    @Test
    void HU25_sin_actividades_no_se_puede_cerrar() {
        OrdenTrabajo o = otListaParaCerrar();
        o.getActividades().clear();

        ApiException e = assertThrows(ApiException.class, () -> servicio.cerrar(carlos, 500L));

        assertEquals("Agrega al menos una actividad realizada (paso 3).", e.getMessage());
    }

    @Test
    void HU22_sin_materiales_no_se_puede_cerrar() {
        OrdenTrabajo o = otListaParaCerrar();
        o.getMateriales().clear();

        ApiException e = assertThrows(ApiException.class, () -> servicio.cerrar(carlos, 500L));

        assertEquals("Agrega al menos un material utilizado (paso 3).", e.getMessage());
    }

    @Test
    void HU20_HU21_sin_fotos_de_antes_o_de_despues_no_se_puede_cerrar() {
        OrdenTrabajo o = otListaParaCerrar();
        when(fotos.countByOrdenIdAndTipo(500L, TipoFoto.DESPUES)).thenReturn(0L);

        ApiException e = assertThrows(ApiException.class, () -> servicio.cerrar(carlos, 500L));

        assertEquals("Debes agregar al menos una foto antes y una después (paso 3).", e.getMessage());
        assertEquals(EstadoOt.EN_EJECUCION, o.getEstado());
    }

    @Test
    void HU25_solo_se_cierra_una_OT_En_ejecucion() {
        ot(EstadoOt.ACEPTADA, carlos);

        esperarError(HttpStatus.CONFLICT, () -> servicio.cerrar(carlos, 500L));
    }

    // ------------------------------------------------------------------
    // HU-37, HU-38: SLA
    // ------------------------------------------------------------------

    @Test
    void HU38_una_atencion_dentro_del_SLA_cumple() {
        assertTrue(OrdenService.cumpleSla(150, 4));
        assertTrue(OrdenService.cumpleSla(240, 4)); // justo el límite
    }

    @Test
    void HU38_pasarse_un_minuto_del_SLA_ya_incumple() {
        assertFalse(OrdenService.cumpleSla(241, 4));
        assertFalse(OrdenService.cumpleSla(410, 6));
    }

    // ------------------------------------------------------------------
    // HU-40, HU-42, HU-43: cliente
    // ------------------------------------------------------------------

    @Test
    void HU40_el_cliente_solicita_una_mantencion_de_un_equipo_de_su_local() {
        OrdenDto dto = servicio.solicitar(clienteIndependencia, new SolicitudRequest(100L, "  Gotea agua  "));

        assertEquals(EstadoOt.CREADA, dto.estado());
        assertNull(dto.tecnicoId());
        assertNull(dto.fecha());
        assertEquals(Prioridad.MEDIA, dto.prioridad());
        assertEquals("Gotea agua", dto.descripcion());
        assertEquals("Solicitada por el cliente", dto.observacion());
    }

    @Test
    void HU40_el_cliente_no_puede_solicitar_mantencion_de_un_equipo_de_otro_local() {
        esperarError(HttpStatus.FORBIDDEN,
                () -> servicio.solicitar(clienteIndependencia, new SolicitudRequest(101L, "Falla")));
    }

    @Test
    void HU42_aprobar_el_trabajo_registra_la_conformidad_con_fecha_y_hora() {
        OrdenTrabajo o = ot(EstadoOt.CERRADA, carlos);

        OrdenDto dto = servicio.aprobar(clienteIndependencia, 500L);

        assertEquals(Conformidad.APROBADA, dto.conformidad());
        assertNotNull(o.getFechaConformidad());
    }

    @Test
    void HU42_no_se_puede_aprobar_dos_veces() {
        OrdenTrabajo o = ot(EstadoOt.CERRADA, carlos);
        o.setConformidad(Conformidad.APROBADA);

        esperarError(HttpStatus.CONFLICT, () -> servicio.aprobar(clienteIndependencia, 500L));
    }

    @Test
    void HU42_solo_se_da_conformidad_a_una_OT_cerrada() {
        ot(EstadoOt.EN_EJECUCION, carlos);

        esperarError(HttpStatus.CONFLICT, () -> servicio.aprobar(clienteIndependencia, 500L));
    }

    @Test
    void HU42_un_cliente_no_puede_aprobar_la_OT_de_otro_local() {
        OrdenTrabajo o = Fabrica.orden(500, 1043, camaraMaipu, EstadoOt.CERRADA, carlos);
        when(ordenes.findById(500L)).thenReturn(Optional.of(o));

        esperarError(HttpStatus.NOT_FOUND, () -> servicio.aprobar(clienteIndependencia, 500L));
    }

    @Test
    void HU43_rechazar_el_trabajo_guarda_el_comentario_del_cliente() {
        OrdenTrabajo o = ot(EstadoOt.CERRADA, carlos);

        OrdenDto dto = servicio.observar(clienteIndependencia, 500L, "  Falta limpiar el área  ");

        assertEquals(Conformidad.OBSERVADA, dto.conformidad());
        assertEquals("Falta limpiar el área", o.getComentarioCliente());
    }

    @Test
    void HU43_el_comentario_es_obligatorio() {
        OrdenTrabajo o = ot(EstadoOt.CERRADA, carlos);

        esperarError(HttpStatus.BAD_REQUEST, () -> servicio.observar(clienteIndependencia, 500L, "  "));

        assertEquals(Conformidad.SIN_REVISAR, o.getConformidad());
    }

    // ------------------------------------------------------------------
    // HU-29, HU-30: clima de la OT
    // ------------------------------------------------------------------

    @Test
    void HU29_una_OT_sin_fecha_no_tiene_pronostico() {
        OrdenTrabajo o = ot(EstadoOt.CREADA, null);
        o.setFecha(null);

        esperarError(HttpStatus.CONFLICT, () -> servicio.climaDeOrden(planificador, 500L));
    }

    @Test
    void HU29_el_clima_de_una_OT_usa_las_coordenadas_de_su_local_y_si_el_equipo_es_exterior() {
        OrdenTrabajo o = ot(EstadoOt.ASIGNADA, carlos);

        servicio.climaDeOrden(planificador, 500L);

        org.mockito.Mockito.verify(clima).consultar(-33.4150, -70.6580, o.getFecha(), true);
    }

    @Test
    void una_OT_nueva_no_cuenta_fotos_porque_aun_no_tiene_id() {
        OrdenDto dto = servicio.crear(peticion(null, LocalDate.now()));

        assertEquals(0, dto.fotosAntes());
        assertEquals(0, dto.fotosDespues());
    }
}
