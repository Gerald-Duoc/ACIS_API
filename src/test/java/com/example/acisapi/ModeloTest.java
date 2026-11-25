package com.example.acisapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.acisapi.dto.Mapeos;
import com.example.acisapi.dto.Respuestas.EquipoDto;
import com.example.acisapi.dto.Respuestas.UsuarioDto;
import com.example.acisapi.modelo.Cliente;
import com.example.acisapi.modelo.Conformidad;
import com.example.acisapi.modelo.Equipo;
import com.example.acisapi.modelo.EstadoOt;
import com.example.acisapi.modelo.Local;
import com.example.acisapi.modelo.OrdenTrabajo;
import com.example.acisapi.modelo.Perfil;
import com.example.acisapi.modelo.Usuario;
import com.example.acisapi.seguridad.Permisos;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Reglas simples del modelo y de la conversión a DTO. */
class ModeloTest {

    private final Cliente cordillera = Fabrica.cliente(1, "Supermercado Cordillera", 4);
    private final Local independencia = Fabrica.local(10, cordillera, "Sucursal Independencia");
    private final Local maipu = Fabrica.local(11, cordillera, "Sucursal Maipú");
    private final Equipo rooftop = Fabrica.equipo(100, independencia, "Condensadora rooftop 1", true);

    @Test
    void el_correo_del_usuario_se_guarda_en_minusculas_y_sin_espacios() {
        Usuario u = new Usuario("Ana", "  Ana.Perez@ACIS.cl ", "hash", Perfil.CLIENTE);

        assertEquals("ana.perez@acis.cl", u.getCorreo());
    }

    @Test
    void el_codigo_visible_de_la_OT_usa_su_numero() {
        OrdenTrabajo o = Fabrica.orden(1, 1041, rooftop, EstadoOt.ASIGNADA, null);

        assertEquals("OT-1041", o.getCodigo());
    }

    @Test
    void una_OT_nueva_parte_sin_revisar_y_sin_listas_nulas() {
        OrdenTrabajo o = new OrdenTrabajo();

        assertEquals(Conformidad.SIN_REVISAR, o.getConformidad());
        assertTrue(o.getActividades().isEmpty());
        assertTrue(o.getMateriales().isEmpty());
        assertEquals(false, o.isUbicacionAutorizada());
    }

    @Test
    void los_estados_tienen_la_misma_etiqueta_que_la_app() {
        assertEquals("Creada", EstadoOt.CREADA.etiqueta());
        assertEquals("Pendiente de asignación", EstadoOt.PENDIENTE_ASIGNACION.etiqueta());
        assertEquals("En ejecución", EstadoOt.EN_EJECUCION.etiqueta());
        assertEquals("Pendiente por imprevisto", EstadoOt.PENDIENTE_IMPREVISTO.etiqueta());
        assertEquals("Cerrada", EstadoOt.CERRADA.etiqueta());
    }

    @Test
    void los_nombres_de_los_estados_coinciden_con_los_de_la_app_Android() {
        List<String> esperados = List.of("CREADA", "PENDIENTE_ASIGNACION", "ASIGNADA", "ACEPTADA",
                "EN_EJECUCION", "PENDIENTE_IMPREVISTO", "CERRADA");

        List<String> reales = Arrays.stream(EstadoOt.values()).map(Enum::name).toList();

        assertEquals(esperados, reales);
    }

    @Test
    void asociar_un_usuario_a_un_local_tambien_lo_asocia_a_su_cliente() {
        Usuario u = new Usuario("Ana", "ana@acis.cl", "hash", Perfil.CLIENTE);

        u.asociarALocal(independencia);

        assertEquals(independencia, u.getLocal());
        assertEquals(cordillera, u.getCliente());
    }

    @Test
    void la_conversion_a_DTO_de_un_usuario_no_incluye_la_clave() {
        Usuario u = Fabrica.usuario(1, "Carlos", "tecnico@acis.cl", Perfil.TECNICO);

        UsuarioDto dto = Mapeos.usuario(u);

        assertEquals("tecnico@acis.cl", dto.correo());
        assertNull(dto.clienteId());
        assertNull(dto.localId());
        assertTrue(!dto.toString().contains("hash"));
    }

    @Test
    void la_conversion_a_DTO_de_un_equipo_conserva_si_es_exterior() {
        EquipoDto dto = Mapeos.equipo(rooftop);

        assertTrue(dto.exterior());
        assertEquals(10L, dto.localId());
    }

    @Test
    void el_planificador_ve_todas_las_OT() {
        Usuario plani = Fabrica.usuario(3, "Plani", "p@acis.cl", Perfil.PLANIFICADOR);
        OrdenTrabajo o = Fabrica.orden(1, 1041, rooftop, EstadoOt.ASIGNADA, null);

        assertTrue(Permisos.puedeVer(plani, o));
    }

    @Test
    void un_cliente_sin_local_asociado_no_ve_ninguna_OT() {
        Usuario sinLocal = Fabrica.usuario(5, "Sin local", "x@acis.cl", Perfil.CLIENTE);
        OrdenTrabajo o = Fabrica.orden(1, 1041, rooftop, EstadoOt.ASIGNADA, null);

        assertEquals(false, Permisos.puedeVer(sinLocal, o));
    }

    @Test
    void un_cliente_ve_las_OT_de_su_local_pero_no_las_de_otro_local_de_la_misma_empresa() {
        Usuario ana = Fabrica.usuario(4, "Ana", "ana@acis.cl", Perfil.CLIENTE);
        ana.asociarALocal(independencia);
        Equipo camaraMaipu = Fabrica.equipo(101, maipu, "Cámara frigorífica 2", false);

        assertTrue(Permisos.puedeVer(ana, Fabrica.orden(1, 1041, rooftop, EstadoOt.ASIGNADA, null)));
        assertEquals(false, Permisos.puedeVer(ana, Fabrica.orden(2, 1044, camaraMaipu, EstadoOt.CREADA, null)));
    }
}
