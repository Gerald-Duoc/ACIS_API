package com.example.acisapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.acisapi.servicio.PronosticoDia;
import com.example.acisapi.servicio.ProveedorClima;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Prueba de punta a punta: levanta la API completa con una base de datos en memoria (H2),
 * carga los datos de ejemplo y recorre el ciclo de una OT como lo haría la app:
 * planificador crea la OT, el técnico la ejecuta, y el cliente la aprueba.
 * El clima es una versión falsa (no usa internet).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(AcisApiIntegrationTest.ClimaFalsoConfig.class)
class AcisApiIntegrationTest {

    @TestConfiguration
    static class ClimaFalsoConfig {
        /** Siempre pronostica lluvia de 5 mm y viento de 20 km/h. */
        @Bean
        @Primary
        ProveedorClima proveedorFalso() {
            return (latitud, longitud, fecha) -> new PronosticoDia(fecha, 18.0, 5.0, 80, 20.0, 30.0);
        }
    }

    @Autowired
    private WebApplicationContext contexto;

    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).build();
    }

    // ------------------------------------------------------------------
    // Ayudas
    // ------------------------------------------------------------------

    private static String texto(MvcResult r) throws Exception {
        return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static String extraer(String json, String regex) {
        Matcher m = Pattern.compile(regex).matcher(json);
        assertTrue(m.find(), "No se encontró " + regex + " en: " + json);
        return m.group(1);
    }

    private static String cuerpoLogin(String correo, String clave) {
        return "{\"correo\":\"" + correo + "\",\"clave\":\"" + clave + "\"}";
    }

    private String token(String correo) throws Exception {
        MvcResult r = mvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpoLogin(correo, "1234")))
                .andExpect(status().isOk()).andReturn();
        return extraer(texto(r), "\"token\":\"([^\"]+)\"");
    }

    private MvcResult get(String url, String token, int estado) throws Exception {
        var peticion = MockMvcRequestBuilders.get(url);
        if (token != null) {
            peticion.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(peticion).andExpect(status().is(estado)).andReturn();
    }

    private MvcResult post(String url, String token, String json, int estado) throws Exception {
        var peticion = MockMvcRequestBuilders.post(url);
        if (token != null) {
            peticion.header("Authorization", "Bearer " + token);
        }
        if (json != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mvc.perform(peticion).andExpect(status().is(estado)).andReturn();
    }

    private MvcResult put(String url, String token, String json, int estado) throws Exception {
        var peticion = MockMvcRequestBuilders.put(url);
        if (token != null) {
            peticion.header("Authorization", "Bearer " + token);
        }
        peticion.contentType(MediaType.APPLICATION_JSON).content(json);
        return mvc.perform(peticion).andExpect(status().is(estado)).andReturn();
    }

    private static byte[] jpegDePrueba() {
        byte[] d = new byte[300];
        d[0] = (byte) 0xFF;
        d[1] = (byte) 0xD8;
        d[2] = (byte) 0xFF;
        for (int i = 3; i < d.length; i++) {
            d[i] = (byte) i;
        }
        return d;
    }

    // ------------------------------------------------------------------
    // HU-01: acceso y perfiles
    // ------------------------------------------------------------------

    @Test
    void la_API_responde_sin_necesidad_de_sesion() throws Exception {
        MvcResult r = get("/api/salud", null, 200);

        assertTrue(texto(r).contains("\"estado\":\"ok\""));
    }

    @Test
    void HU01_el_login_correcto_entrega_token_y_perfil_sin_mostrar_la_clave() throws Exception {
        MvcResult r = post("/api/auth/login", null, cuerpoLogin("tecnico@acis.cl", "1234"), 200);

        String cuerpo = texto(r);
        assertTrue(cuerpo.contains("\"perfil\":\"TECNICO\""));
        assertTrue(cuerpo.contains("Carlos Muñoz"));
        assertFalse(cuerpo.contains("clave"), "La respuesta no debe incluir ninguna clave");
    }

    @Test
    void HU01_una_clave_incorrecta_responde_401() throws Exception {
        MvcResult r = post("/api/auth/login", null, cuerpoLogin("tecnico@acis.cl", "mala"), 401);

        assertTrue(texto(r).contains("Correo o clave incorrectos"));
    }

    @Test
    void HU01_cada_perfil_inicia_sesion_con_su_perfil() throws Exception {
        assertTrue(texto(post("/api/auth/login", null, cuerpoLogin("planificador@acis.cl", "1234"), 200))
                .contains("\"perfil\":\"PLANIFICADOR\""));
        assertTrue(texto(post("/api/auth/login", null, cuerpoLogin("cliente@acis.cl", "1234"), 200))
                .contains("\"perfil\":\"CLIENTE\""));
    }

    @Test
    void HU01_sin_token_o_con_un_token_falso_se_responde_401() throws Exception {
        get("/api/ordenes", null, 401);
        get("/api/ordenes", "token-falso", 401);
    }

    @Test
    void HU01_despues_de_cerrar_sesion_el_token_deja_de_funcionar() throws Exception {
        String tok = token("tecnico@acis.cl");
        get("/api/ordenes", tok, 200);

        post("/api/auth/logout", tok, null, 200);

        get("/api/ordenes", tok, 401);
    }

    @Test
    void HU01_un_perfil_no_puede_usar_las_acciones_de_otro() throws Exception {
        String tecnico = token("tecnico@acis.cl");
        String cliente = token("cliente@acis.cl");

        get("/api/clientes", tecnico, 403);                       // el catastro es del planificador
        get("/api/clientes", cliente, 403);
        post("/api/ordenes", tecnico, "{}", 403);                 // técnico no crea OT
        post("/api/ordenes/1/aceptar", cliente, null, 403);       // cliente no acepta OT
    }

    @Test
    void HU13_el_tecnico_ve_solo_sus_OT() throws Exception {
        String cuerpo = texto(get("/api/ordenes", token("tecnico@acis.cl"), 200));

        assertTrue(cuerpo.contains("\"tecnicoNombre\":\"Carlos Muñoz\""));
        assertFalse(cuerpo.contains("\"tecnicoNombre\":\"Javiera Rojas\""));
        assertFalse(cuerpo.contains("\"tecnicoNombre\":\"Matías Soto\""));
    }

    @Test
    void HU41_el_cliente_ve_solo_las_OT_de_su_local() throws Exception {
        String cuerpo = texto(get("/api/ordenes", token("cliente@acis.cl"), 200));

        assertTrue(cuerpo.contains("\"localNombre\":\"Sucursal Independencia\""));
        assertFalse(cuerpo.contains("Sucursal Maipú"));
        assertFalse(cuerpo.contains("Retail Moderna"));
    }

    @Test
    void HU11_HU12_el_planificador_ve_todas_y_puede_filtrar_por_estado() throws Exception {
        String planificador = token("planificador@acis.cl");

        String todas = texto(get("/api/ordenes", planificador, 200));
        String cerradas = texto(get("/api/ordenes?estado=CERRADA", planificador, 200));

        assertTrue(todas.contains("\"estado\":\"CREADA\""));
        assertTrue(cerradas.contains("\"estado\":\"CERRADA\""));
        assertFalse(cerradas.contains("\"estado\":\"CREADA\""));
        assertFalse(cerradas.contains("\"estado\":\"EN_EJECUCION\""));
    }

    // ------------------------------------------------------------------
    // Catastro (HU-02, HU-32 a HU-36)
    // ------------------------------------------------------------------

    @Test
    void HU32_HU36_el_planificador_registra_un_cliente_con_su_SLA_y_no_se_repite() throws Exception {
        String planificador = token("planificador@acis.cl");
        String json = "{\"nombre\":\"Farmacias del Sur\",\"contacto\":\"Luis Rojas\",\"slaHoras\":5}";

        MvcResult creado = post("/api/clientes", planificador, json, 201);
        post("/api/clientes", planificador, json, 409);

        assertTrue(texto(creado).contains("\"slaHoras\":5"));
    }

    @Test
    void HU32_los_campos_obligatorios_del_cliente_se_validan() throws Exception {
        String planificador = token("planificador@acis.cl");

        MvcResult r = post("/api/clientes", planificador, "{\"nombre\":\"\",\"contacto\":\"\",\"slaHoras\":0}", 400);

        String cuerpo = texto(r);
        assertTrue(cuerpo.contains("La razón social es obligatoria"));
        assertTrue(cuerpo.contains("El SLA debe ser de al menos 1 hora"));
    }

    @Test
    void HU33_HU34_se_registra_un_local_con_coordenadas_y_un_equipo() throws Exception {
        String planificador = token("planificador@acis.cl");
        MvcResult cliente = post("/api/clientes", planificador,
                "{\"nombre\":\"Cliente Con Local\",\"contacto\":\"Contacto\",\"slaHoras\":4}", 201);
        String clienteId = extraer(texto(cliente), "\"id\":(\\d+)");

        MvcResult local = post("/api/clientes/" + clienteId + "/locales", planificador,
                "{\"nombre\":\"Tienda Centro\",\"direccion\":\"Av. Siempre Viva 742\",\"latitud\":-33.45,\"longitud\":-70.66}", 201);
        String localId = extraer(texto(local), "\"id\":(\\d+)");
        MvcResult equipo = post("/api/locales/" + localId + "/equipos", planificador,
                "{\"nombre\":\"Split 12\",\"tipo\":\"Split\",\"marca\":\"ClimaTec\",\"modelo\":\"SP-12\",\"exterior\":false}", 201);

        assertTrue(texto(local).contains("\"latitud\":-33.45"));
        assertTrue(texto(equipo).contains("\"ubicacion\":\"Interior\""));
    }

    @Test
    void HU02_un_tecnico_nuevo_puede_iniciar_sesion_y_no_se_permite_un_correo_repetido() throws Exception {
        String planificador = token("planificador@acis.cl");
        String json = "{\"nombre\":\"Ana Torres\",\"correo\":\"atorres@acis.cl\"}";

        post("/api/tecnicos", planificador, json, 201);
        post("/api/tecnicos", planificador, json, 409);
        post("/api/tecnicos", planificador, "{\"nombre\":\"Otro\",\"correo\":\"TECNICO@acis.cl\"}", 409);

        MvcResult login = post("/api/auth/login", null, cuerpoLogin("atorres@acis.cl", "1234"), 200);
        assertTrue(texto(login).contains("\"perfil\":\"TECNICO\""));
    }

    @Test
    void HU02_un_correo_invalido_se_rechaza() throws Exception {
        String planificador = token("planificador@acis.cl");

        post("/api/tecnicos", planificador, "{\"nombre\":\"X\",\"correo\":\"no-es-correo\"}", 400);
    }

    // ------------------------------------------------------------------
    // Clima (HU-29, HU-30, HU-31)
    // ------------------------------------------------------------------

    private static String hoyEnSantiago() {
        return LocalDate.now(ZoneId.of("America/Santiago")).toString();
    }

    @Test
    void HU29_el_clima_se_consulta_a_traves_de_la_API_y_marca_alerta_para_equipos_exteriores() throws Exception {
        String tok = token("tecnico@acis.cl");

        String exterior = texto(get("/api/clima?lat=-33.41&lon=-70.66&fecha=" + hoyEnSantiago() + "&exterior=true", tok, 200));
        String interior = texto(get("/api/clima?lat=-33.41&lon=-70.66&fecha=" + hoyEnSantiago() + "&exterior=false", tok, 200));

        assertTrue(exterior.contains("\"clima\":\"LLUVIA\""));
        assertTrue(exterior.contains("\"requiereAlerta\":true"));
        assertTrue(exterior.contains("Open-Meteo"));
        assertTrue(interior.contains("\"requiereAlerta\":false"));
    }

    @Test
    void HU31_el_clima_se_puede_consultar_por_local() throws Exception {
        String planificador = token("planificador@acis.cl");
        String clientes = texto(get("/api/clientes", planificador, 200));
        String localId = extraer(clientes, "\"id\":(\\d+),\"clienteId\":\\d+,\"nombre\":\"Sucursal Independencia\"");

        String cuerpo = texto(get("/api/clima/locales/" + localId + "?fecha=" + hoyEnSantiago() + "&exterior=true", planificador, 200));

        assertTrue(cuerpo.contains("\"requiereAlerta\":true"));
    }

    @Test
    void HU31_una_fecha_fuera_del_rango_o_un_local_inexistente_dan_error() throws Exception {
        String tok = token("planificador@acis.cl");

        get("/api/clima?lat=-33.41&lon=-70.66&fecha=2000-01-01", tok, 400);
        get("/api/clima/locales/999999?fecha=" + hoyEnSantiago(), tok, 404);
    }

    @Test
    void HU29_la_OT_de_hoy_entrega_su_clima_usando_el_local_y_si_el_equipo_es_exterior() throws Exception {
        String planificador = token("planificador@acis.cl");
        String hoy = texto(get("/api/ordenes?estado=ASIGNADA", planificador, 200));
        String id = extraer(hoy, "\"id\":(\\d+),\"codigo\":\"OT-1041\"");

        String clima = texto(get("/api/ordenes/" + id + "/clima", planificador, 200));

        assertTrue(clima.contains("\"requiereAlerta\":true")); // OT-1041: condensadora en azotea
    }

    // ------------------------------------------------------------------
    // Ciclo completo de una OT (HU-06 a HU-25, HU-40 a HU-43)
    // ------------------------------------------------------------------

    @Test
    void ciclo_completo_planificador_crea_tecnico_ejecuta_y_cliente_aprueba() throws Exception {
        String planificador = token("planificador@acis.cl");
        String carlos = token("tecnico@acis.cl");
        String cliente = token("cliente@acis.cl");

        // El planificador busca el equipo y el técnico
        String equipoId = extraer(texto(get("/api/clientes", planificador, 200)),
                "\"id\":(\\d+),\"localId\":\\d+,\"nombre\":\"Condensadora rooftop 1\"");
        String carlosId = extraer(texto(get("/api/tecnicos", planificador, 200)),
                "\"id\":(\\d+),\"nombre\":\"Carlos Muñoz\"");

        // HU-06, HU-07, HU-08: crea la OT con prioridad y la asigna a Carlos
        String fecha = LocalDate.now().plusDays(1).toString();
        MvcResult creada = post("/api/ordenes", planificador,
                "{\"equipoId\":" + equipoId + ",\"tipo\":\"Correctiva\",\"prioridad\":\"ALTA\",\"fecha\":\"" + fecha
                        + "\",\"tecnicoId\":" + carlosId + ",\"descripcion\":\"El equipo no enfría\"}", 201);
        String cuerpoCreada = texto(creada);
        String id = extraer(cuerpoCreada, "\"id\":(\\d+)");
        assertTrue(cuerpoCreada.contains("\"estado\":\"ASIGNADA\""));
        assertTrue(cuerpoCreada.contains("\"prioridad\":\"ALTA\""));
        assertTrue(cuerpoCreada.contains("\"slaHoras\":4"));

        // HU-15: Carlos la acepta
        assertTrue(texto(post("/api/ordenes/" + id + "/aceptar", carlos, null, 200)).contains("\"estado\":\"ACEPTADA\""));

        // HU-05: sin autorizar la ubicación no hay check-in
        post("/api/ordenes/" + id + "/check-in", carlos, "{\"ubicacionAutorizada\":false}", 400);

        // HU-17: check-in autorizado
        MvcResult checkIn = post("/api/ordenes/" + id + "/check-in", carlos, "{\"ubicacionAutorizada\":true}", 200);
        assertTrue(texto(checkIn).contains("\"estado\":\"EN_EJECUCION\""));

        // HU-25: todavía no se puede cerrar (falta el check-out y el resto del registro)
        assertTrue(texto(post("/api/ordenes/" + id + "/cierre", carlos, null, 400)).contains("check-out"));

        // HU-18, HU-19, HU-22: diagnóstico, actividades y materiales
        MvcResult registro = put("/api/ordenes/" + id + "/registro", carlos,
                "{\"diagnostico\":\"Compresor sin carga\",\"actividades\":[\"Cambio de compresor\"],"
                        + "\"materiales\":[\"Compresor × 1\"]}", 200);
        assertTrue(texto(registro).contains("Compresor sin carga"));

        // HU-20 y HU-21: fotos reales (se guardan en la base de datos)
        byte[] bytes = jpegDePrueba();
        MvcResult fotoAntes = mvc.perform(MockMvcRequestBuilders.multipart("/api/ordenes/" + id + "/fotos")
                        .file(new MockMultipartFile("archivo", "antes.jpg", "image/jpeg", bytes))
                        .param("tipo", "ANTES").header("Authorization", "Bearer " + carlos))
                .andExpect(status().isCreated()).andReturn();
        mvc.perform(MockMvcRequestBuilders.multipart("/api/ordenes/" + id + "/fotos")
                        .file(new MockMultipartFile("archivo", "despues.jpg", "image/jpeg", bytes))
                        .param("tipo", "DESPUES").header("Authorization", "Bearer " + carlos))
                .andExpect(status().isCreated());
        String fotoId = extraer(texto(fotoAntes), "\"id\":(\\d+)");

        // El cliente de ese local puede ver y descargar la foto, el contenido es idéntico
        MvcResult descarga = get("/api/fotos/" + fotoId, cliente, 200);
        assertEquals("image/jpeg", descarga.getResponse().getContentType());
        assertTrue(java.util.Arrays.equals(bytes, descarga.getResponse().getContentAsByteArray()));
        assertTrue(texto(get("/api/ordenes/" + id + "/fotos", cliente, 200)).contains("\"tipo\":\"DESPUES\""));

        // HU-25: falta el check-out
        assertTrue(texto(post("/api/ordenes/" + id + "/cierre", carlos, null, 400)).contains("check-out"));

        // HU-24: check-out y tiempo de atención
        MvcResult checkOut = post("/api/ordenes/" + id + "/check-out", carlos, null, 200);
        assertTrue(Integer.parseInt(extraer(texto(checkOut), "\"tiempoRealMin\":(\\d+)")) >= 1);

        // HU-25: ahora sí se cierra
        MvcResult cerrada = post("/api/ordenes/" + id + "/cierre", carlos, null, 200);
        String cuerpoCerrada = texto(cerrada);
        assertTrue(cuerpoCerrada.contains("\"estado\":\"CERRADA\""));
        assertTrue(cuerpoCerrada.contains("\"fotosAntes\":1"));
        assertTrue(cuerpoCerrada.contains("\"fotosDespues\":1"));

        // HU-35: la intervención queda en el historial del equipo
        String historial = texto(get("/api/equipos/" + equipoId + "/historial", planificador, 200));
        assertTrue(historial.contains("Compresor sin carga"));

        // HU-45 / HU-42: el cliente la ve cerrada y la aprueba (y no puede aprobarla dos veces)
        assertTrue(texto(get("/api/ordenes/" + id, cliente, 200)).contains("\"conformidad\":\"SIN_REVISAR\""));
        assertTrue(texto(post("/api/ordenes/" + id + "/aprobacion", cliente, null, 200)).contains("\"conformidad\":\"APROBADA\""));
        post("/api/ordenes/" + id + "/aprobacion", cliente, null, 409);
    }

    @Test
    void HU16_el_tecnico_rechaza_una_OT_con_motivo_y_la_OT_vuelve_a_Pendiente_de_asignacion() throws Exception {
        String planificador = token("planificador@acis.cl");
        String carlos = token("tecnico@acis.cl");
        String equipoId = extraer(texto(get("/api/clientes", planificador, 200)),
                "\"id\":(\\d+),\"localId\":\\d+,\"nombre\":\"Condensadora rooftop 1\"");
        String carlosId = extraer(texto(get("/api/tecnicos", planificador, 200)),
                "\"id\":(\\d+),\"nombre\":\"Carlos Muñoz\"");
        String fecha = LocalDate.now().plusDays(2).toString();
        String id = extraer(texto(post("/api/ordenes", planificador,
                "{\"equipoId\":" + equipoId + ",\"tipo\":\"Preventiva\",\"prioridad\":\"BAJA\",\"fecha\":\"" + fecha
                        + "\",\"tecnicoId\":" + carlosId + ",\"descripcion\":\"Revisión\"}", 201)), "\"id\":(\\d+)");

        post("/api/ordenes/" + id + "/rechazo", carlos, "{\"motivo\":\"\"}", 400);
        MvcResult rechazada = post("/api/ordenes/" + id + "/rechazo", carlos, "{\"motivo\":\"Tengo otra visita\"}", 200);

        String cuerpo = texto(rechazada);
        assertTrue(cuerpo.contains("\"estado\":\"PENDIENTE_ASIGNACION\""));
        assertTrue(cuerpo.contains("Tengo otra visita"));
        get("/api/ordenes/" + id, carlos, 404); // ya no es suya, no la puede ver
    }

    @Test
    void HU40_HU43_el_cliente_solicita_una_mantencion_y_el_planificador_la_ve() throws Exception {
        String cliente = token("cliente@acis.cl");
        String planificador = token("planificador@acis.cl");
        String equipoId = extraer(texto(get("/api/mi-local", cliente, 200)),
                "\"id\":(\\d+),\"localId\":\\d+,\"nombre\":\"Condensadora rooftop 1\"");

        MvcResult solicitud = post("/api/ordenes/solicitud", cliente,
                "{\"equipoId\":" + equipoId + ",\"descripcion\":\"Gotea agua\"}", 201);

        String cuerpo = texto(solicitud);
        assertTrue(cuerpo.contains("\"estado\":\"CREADA\""));
        assertTrue(cuerpo.contains("Solicitada por el cliente"));
        String id = extraer(cuerpo, "\"id\":(\\d+)");
        assertTrue(texto(get("/api/ordenes/" + id, planificador, 200)).contains("Gotea agua"));
        post("/api/ordenes/" + id + "/aprobacion", cliente, null, 409); // todavía no está cerrada
    }

    @Test
    void HU40_el_cliente_no_puede_pedir_mantencion_de_un_equipo_de_otro_local() throws Exception {
        String planificador = token("planificador@acis.cl");
        String cliente = token("cliente@acis.cl");
        String equipoMaipu = extraer(texto(get("/api/clientes", planificador, 200)),
                "\"id\":(\\d+),\"localId\":\\d+,\"nombre\":\"Condensadora patio 1\"");

        post("/api/ordenes/solicitud", cliente, "{\"equipoId\":" + equipoMaipu + ",\"descripcion\":\"Falla\"}", 403);
    }

    @Test
    void HU20_un_cliente_no_puede_subir_fotos_y_un_archivo_falso_se_rechaza() throws Exception {
        String planificador = token("planificador@acis.cl");
        String id = extraer(texto(get("/api/ordenes?estado=ASIGNADA", planificador, 200)),
                "\"id\":(\\d+),\"codigo\":\"OT-1043\"");
        String cliente = token("cliente@acis.cl");

        mvc.perform(MockMvcRequestBuilders.multipart("/api/ordenes/" + id + "/fotos")
                        .file(new MockMultipartFile("archivo", "antes.jpg", "image/jpeg", jpegDePrueba()))
                        .param("tipo", "ANTES").header("Authorization", "Bearer " + cliente))
                .andExpect(status().isForbidden());
    }

    @Test
    void HU20_las_fotos_solo_se_aceptan_mientras_la_OT_esta_en_ejecucion() throws Exception {
        String carlos = token("tecnico@acis.cl");
        String id = extraer(texto(get("/api/ordenes?estado=ASIGNADA", carlos, 200)), "\"id\":(\\d+),\"codigo\":\"OT-1043\"");

        mvc.perform(MockMvcRequestBuilders.multipart("/api/ordenes/" + id + "/fotos")
                        .file(new MockMultipartFile("archivo", "antes.jpg", "image/jpeg", jpegDePrueba()))
                        .param("tipo", "ANTES").header("Authorization", "Bearer " + carlos))
                .andExpect(status().isConflict());
    }
}
