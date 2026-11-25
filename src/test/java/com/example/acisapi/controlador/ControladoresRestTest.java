package com.example.acisapi.controlador;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.acisapi.Fabrica;
import com.example.acisapi.dto.FotoResumen;
import com.example.acisapi.dto.Respuestas.ClimaDto;
import com.example.acisapi.dto.Respuestas.LoginResponse;
import com.example.acisapi.dto.Respuestas.OrdenDto;
import com.example.acisapi.dto.Respuestas.UsuarioDto;
import com.example.acisapi.error.ApiException;
import com.example.acisapi.error.GlobalExceptionHandler;
import com.example.acisapi.modelo.Clima;
import com.example.acisapi.modelo.Conformidad;
import com.example.acisapi.modelo.EstadoOt;
import com.example.acisapi.modelo.FotoOrden;
import com.example.acisapi.modelo.Perfil;
import com.example.acisapi.modelo.Prioridad;
import com.example.acisapi.modelo.TipoFoto;
import com.example.acisapi.modelo.Usuario;
import com.example.acisapi.seguridad.AuthInterceptor;
import com.example.acisapi.servicio.AuthService;
import com.example.acisapi.servicio.ClimaService;
import com.example.acisapi.servicio.FotoService;
import com.example.acisapi.servicio.OrdenService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Prueba lo que ve la app: códigos HTTP y mensajes de error de los controladores.
 * Los servicios están simulados, así que aquí solo se revisa la capa REST
 * (validaciones, conversión de datos y respuestas de error).
 */
class ControladoresRestTest {

    private static final String ATR = AuthInterceptor.ATRIBUTO_USUARIO;

    private final OrdenService ordenes = mock(OrdenService.class);
    private final FotoService fotos = mock(FotoService.class);
    private final ClimaService clima = mock(ClimaService.class);
    private final AuthService auth = mock(AuthService.class);

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new OrdenController(ordenes), new FotoController(fotos),
                    new ClimaController(clima), new AuthController(auth))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    private final Usuario tecnico = Fabrica.usuario(1, "Carlos Muñoz", "tecnico@acis.cl", Perfil.TECNICO);

    private static String texto(MvcResult r) throws Exception {
        return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** OrdenDto mínimo para las respuestas simuladas (sin fechas, para no depender de su formato). */
    private static OrdenDto ordenDeEjemplo() {
        return new OrdenDto(500L, "OT-1041", EstadoOt.ASIGNADA, Prioridad.ALTA, "Correctiva", null,
                "No enfría", null, 4, null, 1L, "Supermercado Cordillera", 10L, "Sucursal Independencia",
                "Av. Independencia 1250", 100L, "Condensadora rooftop 1", true, "Azotea", null,
                1L, "Carlos Muñoz", false, null, null, null, List.of(), List.of(), 0, 0,
                Conformidad.SIN_REVISAR, null, null);
    }

    // ------------------------------------------------------------------
    // Validaciones: la API responde 400 con un mensaje claro
    // ------------------------------------------------------------------

    @Test
    void HU16_rechazar_sin_motivo_responde_400_con_el_mensaje() throws Exception {
        MvcResult r = mvc.perform(post("/api/ordenes/500/rechazo").requestAttr(ATR, tecnico)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.estado").value(400))
                .andReturn();

        assertTrue(texto(r).contains("El motivo es obligatorio"));
    }

    @Test
    void HU06_crear_una_OT_sin_datos_responde_400_e_indica_lo_que_falta() throws Exception {
        MvcResult r = mvc.perform(post("/api/ordenes").requestAttr(ATR, tecnico)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        String cuerpo = texto(r);
        assertTrue(cuerpo.contains("El equipo es obligatorio"));
        assertTrue(cuerpo.contains("La prioridad es obligatoria"));
        assertTrue(cuerpo.contains("La fecha es obligatoria"));
    }

    @Test
    void HU06_el_tipo_de_OT_solo_puede_ser_Preventiva_o_Correctiva() throws Exception {
        String cuerpo = "{\"equipoId\":100,\"tipo\":\"Otra\",\"prioridad\":\"ALTA\","
                + "\"fecha\":\"2099-01-01\",\"descripcion\":\"Falla\"}";

        MvcResult r = mvc.perform(post("/api/ordenes").requestAttr(ATR, tecnico)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertTrue(texto(r).contains("Preventiva o Correctiva"));
    }

    @Test
    void HU07_una_prioridad_que_no_existe_responde_400() throws Exception {
        String cuerpo = "{\"equipoId\":100,\"tipo\":\"Correctiva\",\"prioridad\":\"URGENTISIMA\","
                + "\"fecha\":\"2099-01-01\",\"descripcion\":\"Falla\"}";

        mvc.perform(post("/api/ordenes").requestAttr(ATR, tecnico)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest());
    }

    @Test
    void un_JSON_mal_formado_responde_400() throws Exception {
        mvc.perform(post("/api/ordenes/500/rechazo").requestAttr(ATR, tecnico)
                        .contentType(MediaType.APPLICATION_JSON).content("{motivo: sin comillas"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void HU05_el_check_in_sin_indicar_la_autorizacion_responde_400() throws Exception {
        mvc.perform(post("/api/ordenes/500/check-in").requestAttr(ATR, tecnico)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void HU05_el_check_in_con_autorizacion_llama_al_servicio_y_responde_200() throws Exception {
        when(ordenes.checkIn(any(Usuario.class), eq(500L), eq(true))).thenReturn(ordenDeEjemplo());

        mvc.perform(post("/api/ordenes/500/check-in").requestAttr(ATR, tecnico)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ubicacionAutorizada\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("OT-1041"));

        verify(ordenes).checkIn(tecnico, 500L, true);
    }

    // ------------------------------------------------------------------
    // Errores de negocio: se convierten en el código HTTP correcto
    // ------------------------------------------------------------------

    @Test
    void un_error_de_negocio_409_llega_a_la_app_con_su_mensaje() throws Exception {
        when(ordenes.aceptar(any(Usuario.class), eq(500L)))
                .thenThrow(new ApiException(HttpStatus.CONFLICT, "No se puede aceptar una OT en estado \"Cerrada\"."));

        MvcResult r = mvc.perform(post("/api/ordenes/500/aceptar").requestAttr(ATR, tecnico))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.estado").value(409))
                .andReturn();

        assertTrue(texto(r).contains("No se puede aceptar una OT en estado"));
    }

    @Test
    void una_OT_que_no_existe_responde_404() throws Exception {
        when(ordenes.obtener(any(Usuario.class), eq(999L))).thenThrow(ApiException.noEncontrado("La OT no existe."));

        mvc.perform(get("/api/ordenes/999").requestAttr(ATR, tecnico))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("La OT no existe."));
    }

    @Test
    void un_id_que_no_es_numero_responde_400() throws Exception {
        mvc.perform(get("/api/ordenes/abc").requestAttr(ATR, tecnico))
                .andExpect(status().isBadRequest());
    }

    @Test
    void un_error_inesperado_responde_500_sin_mostrar_detalles_internos() throws Exception {
        when(ordenes.aceptar(any(Usuario.class), eq(500L))).thenThrow(new IllegalStateException("detalle interno secreto"));

        MvcResult r = mvc.perform(post("/api/ordenes/500/aceptar").requestAttr(ATR, tecnico))
                .andExpect(status().isInternalServerError())
                .andReturn();

        assertTrue(!texto(r).contains("detalle interno secreto"));
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    @Test
    void HU12_el_listado_acepta_filtros_y_los_entrega_al_servicio() throws Exception {
        when(ordenes.listar(any(Usuario.class), eq(EstadoOt.CERRADA), eq(2L), eq(1L), eq(LocalDate.of(2026, 11, 24))))
                .thenReturn(List.of(ordenDeEjemplo()));

        mvc.perform(get("/api/ordenes?estado=CERRADA&tecnicoId=2&clienteId=1&fecha=2026-11-24").requestAttr(ATR, tecnico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigo").value("OT-1041"))
                .andExpect(jsonPath("$[0].estado").value("ASIGNADA"));
    }

    @Test
    void HU12_un_estado_que_no_existe_en_el_filtro_responde_400() throws Exception {
        mvc.perform(get("/api/ordenes?estado=INVENTADO").requestAttr(ATR, tecnico))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------
    // Clima
    // ------------------------------------------------------------------

    @Test
    void HU29_el_clima_por_coordenadas_se_entrega_con_su_clasificacion_y_la_fuente() throws Exception {
        ClimaDto dto = new ClimaDto(null, Clima.LLUVIA, true, false, true, 18.0, 6.0, 90, 22.0, 35.0,
                ClimaService.FUENTE);
        when(clima.consultar(-33.41, -70.66, LocalDate.of(2026, 11, 24), true)).thenReturn(dto);

        MvcResult r = mvc.perform(get("/api/clima?lat=-33.41&lon=-70.66&fecha=2026-11-24&exterior=true")
                        .requestAttr(ATR, tecnico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clima").value("LLUVIA"))
                .andExpect(jsonPath("$.requiereAlerta").value(true))
                .andReturn();

        assertTrue(texto(r).contains("Open-Meteo"));
    }

    @Test
    void HU29_si_no_se_indica_exterior_se_asume_que_el_equipo_es_interior() throws Exception {
        when(clima.consultar(-33.41, -70.66, LocalDate.of(2026, 11, 24), false))
                .thenReturn(new ClimaDto(null, Clima.DESPEJADO, false, false, false, 20.0, 0.0, 0, 10.0, 15.0, "x"));

        mvc.perform(get("/api/clima?lat=-33.41&lon=-70.66&fecha=2026-11-24").requestAttr(ATR, tecnico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiereAlerta").value(false));
    }

    @Test
    void HU31_una_fecha_con_formato_invalido_responde_400() throws Exception {
        mvc.perform(get("/api/clima?lat=-33.41&lon=-70.66&fecha=mañana").requestAttr(ATR, tecnico))
                .andExpect(status().isBadRequest());
    }

    @Test
    void HU31_si_falta_un_parametro_responde_400() throws Exception {
        mvc.perform(get("/api/clima?lat=-33.41&fecha=2026-11-24").requestAttr(ATR, tecnico))
                .andExpect(status().isBadRequest());
    }

    @Test
    void HU31_el_clima_por_local_llama_al_servicio_con_el_local_elegido() throws Exception {
        when(clima.consultarLocal(10L, LocalDate.of(2026, 11, 24), true))
                .thenReturn(new ClimaDto(null, Clima.VIENTO_FUERTE, false, true, true, 20.0, 0.0, 10, 45.0, 70.0, "x"));

        mvc.perform(get("/api/clima/locales/10?fecha=2026-11-24&exterior=true").requestAttr(ATR, tecnico))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clima").value("VIENTO_FUERTE"));
    }

    @Test
    void el_servicio_del_clima_caido_se_informa_como_503() throws Exception {
        when(clima.consultar(-33.41, -70.66, LocalDate.of(2026, 11, 24), false))
                .thenThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo consultar el servicio del clima."));

        mvc.perform(get("/api/clima?lat=-33.41&lon=-70.66&fecha=2026-11-24").requestAttr(ATR, tecnico))
                .andExpect(status().isServiceUnavailable());
    }

    // ------------------------------------------------------------------
    // Fotos
    // ------------------------------------------------------------------

    @Test
    void HU20_subir_una_foto_responde_201_con_los_datos_de_la_foto() throws Exception {
        MockMultipartFile archivo = new MockMultipartFile("archivo", "antes.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(fotos.subir(any(Usuario.class), eq(500L), eq(TipoFoto.ANTES), any()))
                .thenReturn(new FotoResumen(9L, TipoFoto.ANTES, "antes.jpg", "image/jpeg", 3L, (LocalDateTime) null));

        mvc.perform(multipart("/api/ordenes/500/fotos").file(archivo).param("tipo", "ANTES").requestAttr(ATR, tecnico))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.tipo").value("ANTES"));
    }

    @Test
    void HU20_un_tipo_de_foto_que_no_es_ANTES_ni_DESPUES_responde_400() throws Exception {
        MockMultipartFile archivo = new MockMultipartFile("archivo", "antes.jpg", "image/jpeg", new byte[]{1, 2, 3});

        mvc.perform(multipart("/api/ordenes/500/fotos").file(archivo).param("tipo", "OTRA").requestAttr(ATR, tecnico))
                .andExpect(status().isBadRequest());
    }

    @Test
    void HU20_subir_una_foto_sin_archivo_responde_400() throws Exception {
        mvc.perform(multipart("/api/ordenes/500/fotos").param("tipo", "ANTES").requestAttr(ATR, tecnico))
                .andExpect(status().isBadRequest());
    }

    @Test
    void HU20_la_descarga_entrega_los_bytes_con_su_tipo_de_imagen() throws Exception {
        byte[] bytes = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};
        FotoOrden foto = new FotoOrden(null, TipoFoto.ANTES, "antes.jpg", "image/jpeg", 6L, LocalDateTime.now(), bytes);
        when(fotos.obtener(any(Usuario.class), eq(9L))).thenReturn(foto);

        mvc.perform(get("/api/fotos/9").requestAttr(ATR, tecnico))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"))
                .andExpect(content().bytes(bytes));
    }

    @Test
    void HU20_eliminar_una_foto_responde_204() throws Exception {
        mvc.perform(delete("/api/fotos/9").requestAttr(ATR, tecnico))
                .andExpect(status().isNoContent());

        verify(fotos).eliminar(tecnico, 9L);
    }

    // ------------------------------------------------------------------
    // Login
    // ------------------------------------------------------------------

    @Test
    void HU01_el_login_sin_datos_responde_400_e_indica_lo_que_falta() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        String cuerpo = texto(r);
        assertTrue(cuerpo.contains("El correo es obligatorio"));
        assertTrue(cuerpo.contains("La clave es obligatoria"));
    }

    @Test
    void HU01_el_login_correcto_entrega_el_token_y_el_perfil() throws Exception {
        UsuarioDto usuario = new UsuarioDto(1L, "Carlos Muñoz", "tecnico@acis.cl", Perfil.TECNICO, null, null);
        when(auth.login("tecnico@acis.cl", "1234")).thenReturn(new LoginResponse("tok123", null, usuario));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"tecnico@acis.cl\",\"clave\":\"1234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("tok123"))
                .andExpect(jsonPath("$.usuario.perfil").value("TECNICO"));
    }

    @Test
    void HU01_credenciales_incorrectas_responden_401_con_mensaje() throws Exception {
        when(auth.login("tecnico@acis.cl", "mala"))
                .thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "Correo o clave incorrectos. Inténtalo nuevamente."));

        MvcResult r = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"tecnico@acis.cl\",\"clave\":\"mala\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertEquals(401, r.getResponse().getStatus());
        assertTrue(texto(r).contains("Correo o clave incorrectos"));
    }

    @Test
    void la_comprobacion_de_salud_responde_ok() throws Exception {
        mvc.perform(get("/api/salud"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ok"));
    }

    @Test
    void una_ruta_que_no_existe_responde_404_y_no_500() throws Exception {
        mvc.perform(get("/api/ruta-inexistente").requestAttr(ATR, tecnico))
                .andExpect(status().isNotFound());
    }
}
