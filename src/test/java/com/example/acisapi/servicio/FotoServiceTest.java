package com.example.acisapi.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.acisapi.Fabrica;
import com.example.acisapi.dto.FotoResumen;
import com.example.acisapi.error.ApiException;
import com.example.acisapi.modelo.Cliente;
import com.example.acisapi.modelo.Equipo;
import com.example.acisapi.modelo.EstadoOt;
import com.example.acisapi.modelo.FotoOrden;
import com.example.acisapi.modelo.Local;
import com.example.acisapi.modelo.OrdenTrabajo;
import com.example.acisapi.modelo.Perfil;
import com.example.acisapi.modelo.TipoFoto;
import com.example.acisapi.modelo.Usuario;
import com.example.acisapi.repositorio.FotoRepository;
import com.example.acisapi.repositorio.OrdenRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

/** HU-20 y HU-21: fotografías de la OT guardadas en la base de datos. */
class FotoServiceTest {

    private static final long MAX_BYTES = 1000;

    private final FotoRepository fotos = mock(FotoRepository.class);
    private final OrdenRepository ordenes = mock(OrdenRepository.class);
    private final FotoService servicio = new FotoService(fotos, ordenes, MAX_BYTES);

    private final Cliente cordillera = Fabrica.cliente(1, "Supermercado Cordillera", 4);
    private final Local independencia = Fabrica.local(10, cordillera, "Sucursal Independencia");
    private final Local maipu = Fabrica.local(11, cordillera, "Sucursal Maipú");
    private final Equipo rooftop = Fabrica.equipo(100, independencia, "Condensadora rooftop 1", true);
    private final Usuario carlos = Fabrica.usuario(1, "Carlos Muñoz", "tecnico@acis.cl", Perfil.TECNICO);
    private final Usuario javiera = Fabrica.usuario(2, "Javiera Rojas", "jrojas@acis.cl", Perfil.TECNICO);
    private final Usuario clienteIndependencia = Fabrica.usuario(4, "Ana Pérez", "cliente@acis.cl", Perfil.CLIENTE);
    private final Usuario clienteMaipu = Fabrica.usuario(5, "Luis Soto", "maipu@acis.cl", Perfil.CLIENTE);

    private OrdenTrabajo orden;

    /** Bytes que parecen un JPEG real (empiezan con FF D8 FF). */
    private static byte[] jpeg(int tamano) {
        byte[] d = new byte[tamano];
        d[0] = (byte) 0xFF;
        d[1] = (byte) 0xD8;
        d[2] = (byte) 0xFF;
        return d;
    }

    @BeforeEach
    void preparar() {
        clienteIndependencia.asociarALocal(independencia);
        clienteMaipu.asociarALocal(maipu);
        orden = Fabrica.orden(500, 1041, rooftop, EstadoOt.EN_EJECUCION, carlos);
        when(ordenes.findById(500L)).thenReturn(Optional.of(orden));
        when(fotos.save(any(FotoOrden.class))).thenAnswer(i -> {
            FotoOrden f = i.getArgument(0);
            ReflectionTestUtils.setField(f, "id", 77L);
            return f;
        });
    }

    private static void esperarError(HttpStatus esperado, Runnable accion) {
        ApiException e = assertThrows(ApiException.class, accion::run);
        assertEquals(esperado, e.getEstado());
    }

    // ------------------------------------------------------------------
    // Subir fotos
    // ------------------------------------------------------------------

    @Test
    void HU20_el_tecnico_sube_una_foto_ANTES_y_queda_guardada_con_sus_bytes() {
        byte[] bytes = jpeg(200);
        MockMultipartFile archivo = new MockMultipartFile("archivo", "antes.jpg", "image/jpeg", bytes);

        FotoResumen resumen = servicio.subir(carlos, 500L, TipoFoto.ANTES, archivo);

        assertEquals(77L, resumen.id());
        assertEquals(TipoFoto.ANTES, resumen.tipo());
        assertEquals("image/jpeg", resumen.contentType());
        assertEquals(200L, resumen.tamano());
        verify(fotos).save(any(FotoOrden.class));
    }

    @Test
    void HU21_el_tecnico_sube_una_foto_DESPUES() {
        MockMultipartFile archivo = new MockMultipartFile("archivo", "despues.png", "image/png", pngFalso());

        FotoResumen resumen = servicio.subir(carlos, 500L, TipoFoto.DESPUES, archivo);

        assertEquals(TipoFoto.DESPUES, resumen.tipo());
        assertEquals("image/png", resumen.contentType());
    }

    private static byte[] pngFalso() {
        byte[] d = new byte[50];
        d[0] = (byte) 0x89;
        d[1] = 'P';
        d[2] = 'N';
        d[3] = 'G';
        return d;
    }

    @Test
    void HU20_solo_el_tecnico_asignado_puede_subir_fotos() {
        MockMultipartFile archivo = new MockMultipartFile("archivo", "antes.jpg", "image/jpeg", jpeg(100));

        esperarError(HttpStatus.FORBIDDEN, () -> servicio.subir(javiera, 500L, TipoFoto.ANTES, archivo));
        verify(fotos, never()).save(any(FotoOrden.class));
    }

    @Test
    void HU20_solo_se_suben_fotos_mientras_la_OT_esta_En_ejecucion() {
        orden.setEstado(EstadoOt.ACEPTADA);
        MockMultipartFile archivo = new MockMultipartFile("archivo", "antes.jpg", "image/jpeg", jpeg(100));

        esperarError(HttpStatus.CONFLICT, () -> servicio.subir(carlos, 500L, TipoFoto.ANTES, archivo));
    }

    @Test
    void HU20_un_archivo_vacio_se_rechaza() {
        MockMultipartFile vacio = new MockMultipartFile("archivo", "antes.jpg", "image/jpeg", new byte[0]);

        esperarError(HttpStatus.BAD_REQUEST, () -> servicio.subir(carlos, 500L, TipoFoto.ANTES, vacio));
    }

    @Test
    void HU20_una_foto_mas_grande_que_el_maximo_responde_413() {
        MockMultipartFile grande = new MockMultipartFile("archivo", "antes.jpg", "image/jpeg", jpeg((int) MAX_BYTES + 1));

        esperarError(HttpStatus.valueOf(413), () -> servicio.subir(carlos, 500L, TipoFoto.ANTES, grande));
    }

    @Test
    void HU20_un_archivo_que_no_es_imagen_responde_415() {
        MockMultipartFile pdf = new MockMultipartFile("archivo", "doc.pdf", "application/pdf", jpeg(100));

        esperarError(HttpStatus.UNSUPPORTED_MEDIA_TYPE, () -> servicio.subir(carlos, 500L, TipoFoto.ANTES, pdf));
    }

    @Test
    void HU20_un_archivo_con_nombre_de_foto_pero_contenido_distinto_se_rechaza() {
        byte[] texto = "esto no es una imagen, es solo texto".getBytes();
        MockMultipartFile falso = new MockMultipartFile("archivo", "antes.jpg", "image/jpeg", texto);

        esperarError(HttpStatus.BAD_REQUEST, () -> servicio.subir(carlos, 500L, TipoFoto.ANTES, falso));
        verify(fotos, never()).save(any(FotoOrden.class));
    }

    // ------------------------------------------------------------------
    // Verificación del contenido y del nombre
    // ------------------------------------------------------------------

    @Test
    void se_reconocen_los_tres_formatos_por_sus_primeros_bytes() {
        byte[] webp = new byte[20];
        webp[0] = 'R'; webp[1] = 'I'; webp[2] = 'F'; webp[3] = 'F';
        webp[8] = 'W'; webp[9] = 'E'; webp[10] = 'B'; webp[11] = 'P';

        assertTrue(FotoService.coincideConFormato(jpeg(20), "image/jpeg"));
        assertTrue(FotoService.coincideConFormato(pngFalso(), "image/png"));
        assertTrue(FotoService.coincideConFormato(webp, "image/webp"));
    }

    @Test
    void un_formato_no_coincide_con_otro() {
        assertFalse(FotoService.coincideConFormato(jpeg(20), "image/png"));
        assertFalse(FotoService.coincideConFormato(pngFalso(), "image/jpeg"));
        assertFalse(FotoService.coincideConFormato(new byte[5], "image/jpeg"));
        assertFalse(FotoService.coincideConFormato(null, "image/jpeg"));
        assertFalse(FotoService.coincideConFormato(jpeg(20), "text/plain"));
    }

    @Test
    void el_nombre_del_archivo_se_limpia_de_carpetas_y_caracteres_raros() {
        assertEquals("foto.jpg", FotoService.nombreSeguro(null));
        assertEquals("foto.jpg", FotoService.nombreSeguro("  "));
        assertEquals("secreto.jpg", FotoService.nombreSeguro("../../etc/secreto.jpg"));
        assertEquals("antes_1.jpg", FotoService.nombreSeguro("C:\\fotos\\antes 1.jpg"));
        assertEquals(120, FotoService.nombreSeguro("a".repeat(300) + ".jpg").length());
    }

    // ------------------------------------------------------------------
    // Consultar, descargar y eliminar
    // ------------------------------------------------------------------

    @Test
    void HU20_quien_puede_ver_la_OT_puede_listar_sus_fotos() {
        List<FotoResumen> lista = List.of(
                new FotoResumen(1L, TipoFoto.ANTES, "a.jpg", "image/jpeg", 10L, LocalDateTime.now()));
        when(fotos.resumenes(500L)).thenReturn(lista);

        assertEquals(1, servicio.listar(carlos, 500L).size());
        assertEquals(1, servicio.listar(clienteIndependencia, 500L).size());
    }

    @Test
    void HU41_un_cliente_de_otro_local_no_ve_las_fotos() {
        esperarError(HttpStatus.NOT_FOUND, () -> servicio.listar(clienteMaipu, 500L));
    }

    @Test
    void HU20_un_tecnico_ajeno_a_la_OT_no_ve_las_fotos() {
        esperarError(HttpStatus.NOT_FOUND, () -> servicio.listar(javiera, 500L));
    }

    @Test
    void HU20_la_descarga_devuelve_la_imagen_a_quien_puede_ver_la_OT() {
        FotoOrden foto = new FotoOrden(orden, TipoFoto.ANTES, "a.jpg", "image/jpeg", 20L, LocalDateTime.now(), jpeg(20));
        when(fotos.findById(9L)).thenReturn(Optional.of(foto));

        FotoOrden descargada = servicio.obtener(clienteIndependencia, 9L);

        assertEquals(20, descargada.getDatos().length);
        esperarError(HttpStatus.NOT_FOUND, () -> servicio.obtener(clienteMaipu, 9L));
    }

    @Test
    void HU20_una_foto_que_no_existe_responde_404() {
        when(fotos.findById(404L)).thenReturn(Optional.empty());

        esperarError(HttpStatus.NOT_FOUND, () -> servicio.obtener(carlos, 404L));
    }

    @Test
    void HU20_el_tecnico_puede_eliminar_su_foto_mientras_la_OT_esta_En_ejecucion() {
        FotoOrden foto = new FotoOrden(orden, TipoFoto.ANTES, "a.jpg", "image/jpeg", 20L, LocalDateTime.now(), jpeg(20));
        when(fotos.findById(9L)).thenReturn(Optional.of(foto));

        servicio.eliminar(carlos, 9L);

        verify(fotos).delete(foto);
    }

    @Test
    void HU20_no_se_pueden_eliminar_fotos_de_una_OT_cerrada_ni_de_otro_tecnico() {
        FotoOrden foto = new FotoOrden(orden, TipoFoto.ANTES, "a.jpg", "image/jpeg", 20L, LocalDateTime.now(), jpeg(20));
        when(fotos.findById(9L)).thenReturn(Optional.of(foto));

        esperarError(HttpStatus.FORBIDDEN, () -> servicio.eliminar(javiera, 9L));

        orden.setEstado(EstadoOt.CERRADA);
        esperarError(HttpStatus.CONFLICT, () -> servicio.eliminar(carlos, 9L));
        verify(fotos, never()).delete(any(FotoOrden.class));
    }
}
