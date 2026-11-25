package com.example.acisapi.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.acisapi.Fabrica;
import com.example.acisapi.dto.Peticiones.ClienteRequest;
import com.example.acisapi.dto.Peticiones.EquipoRequest;
import com.example.acisapi.dto.Peticiones.LocalRequest;
import com.example.acisapi.dto.Peticiones.TecnicoRequest;
import com.example.acisapi.dto.Respuestas.ClienteDto;
import com.example.acisapi.dto.Respuestas.EquipoDto;
import com.example.acisapi.dto.Respuestas.HistorialItem;
import com.example.acisapi.dto.Respuestas.LocalDto;
import com.example.acisapi.dto.Respuestas.TecnicoDto;
import com.example.acisapi.error.ApiException;
import com.example.acisapi.modelo.Cliente;
import com.example.acisapi.modelo.Equipo;
import com.example.acisapi.modelo.EstadoOt;
import com.example.acisapi.modelo.Local;
import com.example.acisapi.modelo.OrdenTrabajo;
import com.example.acisapi.modelo.Perfil;
import com.example.acisapi.modelo.Usuario;
import com.example.acisapi.repositorio.ClienteRepository;
import com.example.acisapi.repositorio.EquipoRepository;
import com.example.acisapi.repositorio.LocalRepository;
import com.example.acisapi.repositorio.OrdenRepository;
import com.example.acisapi.repositorio.UsuarioRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Catastro: HU-02, HU-32, HU-33, HU-34, HU-35 y HU-36. */
class CatastroServiceTest {

    private final ClienteRepository clientes = mock(ClienteRepository.class);
    private final LocalRepository locales = mock(LocalRepository.class);
    private final EquipoRepository equipos = mock(EquipoRepository.class);
    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final OrdenRepository ordenes = mock(OrdenRepository.class);
    private final PasswordEncoder codificador = new BCryptPasswordEncoder();
    private final CatastroService servicio =
            new CatastroService(clientes, locales, equipos, usuarios, ordenes, codificador);

    private final Cliente cordillera = Fabrica.cliente(1, "Supermercado Cordillera", 4);
    private final Local independencia = Fabrica.local(10, cordillera, "Sucursal Independencia");

    @BeforeEach
    void preparar() {
        when(clientes.save(any(Cliente.class))).thenAnswer(i -> i.getArgument(0));
        when(locales.save(any(Local.class))).thenAnswer(i -> i.getArgument(0));
        when(equipos.save(any(Equipo.class))).thenAnswer(i -> i.getArgument(0));
        when(usuarios.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
        when(clientes.findById(1L)).thenReturn(Optional.of(cordillera));
        when(locales.findById(10L)).thenReturn(Optional.of(independencia));
    }

    private static void esperarError(HttpStatus esperado, Runnable accion) {
        ApiException e = assertThrows(ApiException.class, accion::run);
        assertEquals(esperado, e.getEstado());
    }

    // --- HU-32 y HU-36: clientes y SLA ---

    @Test
    void HU32_un_cliente_nuevo_queda_registrado_con_razon_social_contacto_y_SLA() {
        ClienteDto dto = servicio.crearCliente(new ClienteRequest("  Farmacias del Sur ", " Luis Rojas · lrojas@fsur.cl ", 5));

        assertEquals("Farmacias del Sur", dto.nombre());
        assertEquals("Luis Rojas · lrojas@fsur.cl", dto.contacto());
        assertEquals(5, dto.slaHoras());
        assertTrue(dto.locales().isEmpty());
    }

    @Test
    void HU36_el_SLA_de_cada_cliente_se_guarda_en_horas() {
        ClienteDto dto = servicio.crearCliente(new ClienteRequest("Cliente SLA", "Contacto", 8));

        assertEquals(8, dto.slaHoras());
    }

    @Test
    void HU32_no_se_permiten_dos_clientes_con_la_misma_razon_social() {
        when(clientes.existsByNombreIgnoreCase("Supermercado Cordillera")).thenReturn(true);

        esperarError(HttpStatus.CONFLICT,
                () -> servicio.crearCliente(new ClienteRequest("Supermercado Cordillera", "Contacto", 4)));
        verify(clientes, never()).save(any(Cliente.class));
    }

    @Test
    void HU32_el_listado_de_clientes_incluye_sus_locales_y_equipos() {
        Equipo rooftop = Fabrica.equipo(100, independencia, "Condensadora rooftop 1", true);
        when(clientes.findAll()).thenReturn(List.of(cordillera));
        when(locales.findByClienteIdOrderById(1L)).thenReturn(List.of(independencia));
        when(equipos.findByLocalIdOrderById(10L)).thenReturn(List.of(rooftop));

        List<ClienteDto> lista = servicio.listarClientes();

        assertEquals(1, lista.size());
        assertEquals(1, lista.get(0).locales().size());
        assertEquals("Condensadora rooftop 1", lista.get(0).locales().get(0).equipos().get(0).nombre());
    }

    // --- HU-33: locales ---

    @Test
    void HU33_un_local_nuevo_queda_asociado_a_su_cliente_con_direccion_y_coordenadas() {
        LocalDto dto = servicio.crearLocal(1L, new LocalRequest("Tienda Mall Sur", "Av. Vicuña Mackenna 100", -33.5, -70.6));

        assertEquals(1L, dto.clienteId());
        assertEquals("Av. Vicuña Mackenna 100", dto.direccion());
        assertEquals(-33.5, dto.latitud());
        assertEquals(-70.6, dto.longitud());
    }

    @Test
    void HU33_el_cliente_del_local_debe_existir() {
        when(clientes.findById(99L)).thenReturn(Optional.empty());

        esperarError(HttpStatus.NOT_FOUND, () -> servicio.crearLocal(99L, new LocalRequest("X", "Y", -33.0, -70.0)));
    }

    @Test
    void HU33_las_coordenadas_deben_ser_validas() {
        esperarError(HttpStatus.BAD_REQUEST, () -> servicio.crearLocal(1L, new LocalRequest("X", "Y", 95.0, -70.0)));
        esperarError(HttpStatus.BAD_REQUEST, () -> servicio.crearLocal(1L, new LocalRequest("X", "Y", -33.0, 200.0)));
    }

    // --- HU-34: equipos ---

    @Test
    void HU34_un_equipo_nuevo_guarda_tipo_marca_modelo_y_el_local() {
        EquipoDto dto = servicio.crearEquipo(10L,
                new EquipoRequest("Split 12", "Split", "ClimaTec", "SP-12", false, null));

        assertEquals(10L, dto.localId());
        assertEquals("Split", dto.tipo());
        assertEquals("ClimaTec", dto.marca());
        assertEquals("SP-12", dto.modelo());
    }

    @Test
    void HU34_si_no_se_indica_la_ubicacion_se_usa_Exterior_o_Interior() {
        EquipoDto exterior = servicio.crearEquipo(10L, new EquipoRequest("Chiller 2", "Chiller", "ClimaTec", "CH-2", true, " "));
        EquipoDto interior = servicio.crearEquipo(10L, new EquipoRequest("Split 12", "Split", "ClimaTec", "SP-12", false, null));

        assertTrue(exterior.exterior());
        assertEquals("Exterior", exterior.ubicacion());
        assertEquals("Interior", interior.ubicacion());
    }

    @Test
    void HU34_el_local_del_equipo_debe_existir() {
        when(locales.findById(99L)).thenReturn(Optional.empty());

        esperarError(HttpStatus.NOT_FOUND,
                () -> servicio.crearEquipo(99L, new EquipoRequest("X", "Y", "Z", "W", true, null)));
    }

    // --- HU-35: historial ---

    @Test
    void HU35_el_historial_lista_las_OT_cerradas_del_equipo_con_su_diagnostico() {
        Equipo rooftop = Fabrica.equipo(100, independencia, "Condensadora rooftop 1", true);
        Usuario carlos = Fabrica.usuario(1, "Carlos", "tecnico@acis.cl", Perfil.TECNICO);
        OrdenTrabajo cerrada = Fabrica.orden(1, 1030, rooftop, EstadoOt.CERRADA, carlos);
        cerrada.setDiagnostico("Serpentín con suciedad leve.");
        cerrada.setFecha(LocalDate.of(2026, 10, 12));
        when(equipos.existsById(100L)).thenReturn(true);
        when(ordenes.findByEquipoIdAndEstadoOrderByFechaDesc(100L, EstadoOt.CERRADA)).thenReturn(List.of(cerrada));

        List<HistorialItem> historial = servicio.historialEquipo(100L);

        assertEquals(1, historial.size());
        assertEquals("OT-1030", historial.get(0).codigo());
        assertEquals(LocalDate.of(2026, 10, 12), historial.get(0).fecha());
        assertEquals("Serpentín con suciedad leve.", historial.get(0).diagnostico());
    }

    @Test
    void HU35_el_historial_de_un_equipo_que_no_existe_responde_404() {
        when(equipos.existsById(anyLong())).thenReturn(false);

        esperarError(HttpStatus.NOT_FOUND, () -> servicio.historialEquipo(999L));
    }

    // --- HU-02: técnicos ---

    @Test
    void HU02_un_tecnico_nuevo_queda_registrado_con_clave_inicial_cifrada() {
        TecnicoDto dto = servicio.crearTecnico(new TecnicoRequest(" Ana Torres ", "  ATorres@acis.cl ", null));

        assertEquals("Ana Torres", dto.nombre());
        assertEquals("atorres@acis.cl", dto.correo());
        assertEquals(0, dto.otActivas());
        org.mockito.ArgumentCaptor<Usuario> capturado = org.mockito.ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(capturado.capture());
        assertEquals(Perfil.TECNICO, capturado.getValue().getPerfil());
        assertTrue(codificador.matches(CatastroService.CLAVE_INICIAL, capturado.getValue().getClaveHash()));
    }

    @Test
    void HU02_se_puede_elegir_la_clave_del_tecnico() {
        servicio.crearTecnico(new TecnicoRequest("Ana", "ana@acis.cl", "miClave99"));

        org.mockito.ArgumentCaptor<Usuario> capturado = org.mockito.ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(capturado.capture());
        assertTrue(codificador.matches("miClave99", capturado.getValue().getClaveHash()));
        assertFalse(codificador.matches("1234", capturado.getValue().getClaveHash()));
    }

    @Test
    void HU02_no_se_permiten_correos_duplicados() {
        when(usuarios.existsByCorreoIgnoreCase("tecnico@acis.cl")).thenReturn(true);

        esperarError(HttpStatus.CONFLICT,
                () -> servicio.crearTecnico(new TecnicoRequest("Otro Carlos", "tecnico@acis.cl", null)));
        verify(usuarios, never()).save(any(Usuario.class));
    }

    @Test
    void HU08_el_listado_de_tecnicos_muestra_cuantas_OT_activas_tiene_cada_uno() {
        Usuario carlos = Fabrica.usuario(1, "Carlos Muñoz", "tecnico@acis.cl", Perfil.TECNICO);
        when(usuarios.findByPerfilOrderByNombre(Perfil.TECNICO)).thenReturn(List.of(carlos));
        when(ordenes.countByTecnicoIdAndEstadoNot(1L, EstadoOt.CERRADA)).thenReturn(3L);

        List<TecnicoDto> lista = servicio.listarTecnicos();

        assertEquals(1, lista.size());
        assertEquals(3L, lista.get(0).otActivas());
    }

    // --- Mi local (cliente) ---

    @Test
    void HU40_el_cliente_ve_su_local_con_sus_equipos() {
        Usuario cliente = Fabrica.usuario(4, "Ana Pérez", "cliente@acis.cl", Perfil.CLIENTE);
        cliente.asociarALocal(independencia);
        when(equipos.findByLocalIdOrderById(10L)).thenReturn(
                List.of(Fabrica.equipo(100, independencia, "Condensadora rooftop 1", true)));

        LocalDto dto = servicio.miLocal(cliente);

        assertEquals("Sucursal Independencia", dto.nombre());
        assertEquals(1, dto.equipos().size());
    }

    @Test
    void HU40_un_usuario_sin_local_no_tiene_mi_local() {
        Usuario sinLocal = Fabrica.usuario(5, "Sin Local", "x@acis.cl", Perfil.CLIENTE);

        esperarError(HttpStatus.NOT_FOUND, () -> servicio.miLocal(sinLocal));
    }
}
