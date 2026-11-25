package com.example.acisapi.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.acisapi.Fabrica;
import com.example.acisapi.dto.Respuestas.ClimaDto;
import com.example.acisapi.error.ApiException;
import com.example.acisapi.modelo.Clima;
import com.example.acisapi.modelo.Local;
import com.example.acisapi.repositorio.LocalRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * HU-29, HU-30 y HU-31: clima real, clasificación y alerta.
 * No usa internet: el proveedor del clima es una versión falsa.
 */
class ClimaServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Santiago");
    private final LocalRepository locales = mock(LocalRepository.class);

    private static LocalDate hoy() {
        return LocalDate.now(ZONA);
    }

    private static PronosticoDia dia(double precipitacionMm, double vientoKmh, double rafagaKmh) {
        return new PronosticoDia(hoy(), 20.0, precipitacionMm, 50, vientoKmh, rafagaKmh);
    }

    private ClimaService servicio(ProveedorClima proveedor) {
        return new ClimaService(proveedor, locales, 1.0, 40.0, "America/Santiago");
    }

    // ------------------------------------------------------------------
    // Clasificación
    // ------------------------------------------------------------------

    @Test
    void HU29_una_precipitacion_de_1_mm_o_mas_es_lluvia() {
        assertEquals(Clima.LLUVIA, ClimaService.clasificar(dia(1.0, 10, 15), 1.0, 40.0));
        assertEquals(Clima.LLUVIA, ClimaService.clasificar(dia(12.5, 10, 15), 1.0, 40.0));
    }

    @Test
    void HU29_menos_de_1_mm_no_es_lluvia() {
        assertEquals(Clima.DESPEJADO, ClimaService.clasificar(dia(0.9, 10, 15), 1.0, 40.0));
        assertFalse(ClimaService.hayLluvia(dia(0.0, 10, 15), 1.0));
    }

    @Test
    void HU30_un_viento_de_40_kmh_o_mas_es_viento_fuerte() {
        assertEquals(Clima.VIENTO_FUERTE, ClimaService.clasificar(dia(0, 40, 45), 1.0, 40.0));
    }

    @Test
    void HU30_un_viento_de_menos_de_40_kmh_no_es_viento_fuerte() {
        assertEquals(Clima.DESPEJADO, ClimaService.clasificar(dia(0, 39.9, 45), 1.0, 40.0));
    }

    @Test
    void HU30_las_rafagas_de_60_kmh_o_mas_tambien_cuentan_como_viento_fuerte() {
        assertTrue(ClimaService.hayVientoFuerte(dia(0, 20, 60), 40.0));
        assertFalse(ClimaService.hayVientoFuerte(dia(0, 20, 59), 40.0));
    }

    @Test
    void HU29_si_hay_lluvia_y_viento_fuerte_a_la_vez_se_informa_lluvia_pero_se_marcan_ambos() {
        PronosticoDia p = dia(8.0, 55, 80);

        assertEquals(Clima.LLUVIA, ClimaService.clasificar(p, 1.0, 40.0));
        assertTrue(ClimaService.hayLluvia(p, 1.0));
        assertTrue(ClimaService.hayVientoFuerte(p, 40.0));
    }

    // ------------------------------------------------------------------
    // Alerta
    // ------------------------------------------------------------------

    @Test
    void HU29_HU30_un_equipo_exterior_con_lluvia_o_viento_genera_alerta() {
        assertTrue(ClimaService.requiereAlerta(true, Clima.LLUVIA));
        assertTrue(ClimaService.requiereAlerta(true, Clima.VIENTO_FUERTE));
    }

    @Test
    void HU29_HU30_un_equipo_interior_nunca_genera_alerta() {
        assertFalse(ClimaService.requiereAlerta(false, Clima.LLUVIA));
        assertFalse(ClimaService.requiereAlerta(false, Clima.VIENTO_FUERTE));
    }

    @Test
    void HU29_con_el_cielo_despejado_no_hay_alerta_aunque_el_equipo_este_afuera() {
        assertFalse(ClimaService.requiereAlerta(true, Clima.DESPEJADO));
    }

    // ------------------------------------------------------------------
    // Consulta completa
    // ------------------------------------------------------------------

    @Test
    void HU29_la_consulta_entrega_el_pronostico_la_clasificacion_y_la_fuente() {
        ClimaService servicio = servicio((lat, lon, fecha) -> new PronosticoDia(fecha, 18.5, 6.0, 90, 22.0, 35.0));

        ClimaDto dto = servicio.consultar(-33.41, -70.66, hoy(), true);

        assertEquals(Clima.LLUVIA, dto.clima());
        assertTrue(dto.lluvia());
        assertFalse(dto.vientoFuerte());
        assertTrue(dto.requiereAlerta());
        assertEquals(6.0, dto.precipitacionMm());
        assertEquals(90, dto.probabilidadLluviaPct());
        assertEquals(18.5, dto.temperaturaMaxC());
        assertTrue(dto.fuente().contains("Open-Meteo"));
    }

    @Test
    void HU31_la_misma_consulta_para_un_equipo_interior_no_genera_alerta() {
        ClimaService servicio = servicio((lat, lon, fecha) -> new PronosticoDia(fecha, 18.5, 6.0, 90, 22.0, 35.0));

        ClimaDto dto = servicio.consultar(-33.41, -70.66, hoy(), false);

        assertEquals(Clima.LLUVIA, dto.clima());
        assertFalse(dto.requiereAlerta());
    }

    @Test
    void HU31_se_puede_consultar_hasta_15_dias_despues_de_hoy() {
        ClimaService servicio = servicio((lat, lon, fecha) -> dia(0, 10, 15));

        ClimaDto dto = servicio.consultar(-33.41, -70.66, hoy().plusDays(15), true);

        assertEquals(Clima.DESPEJADO, dto.clima());
    }

    @Test
    void HU31_una_fecha_pasada_se_rechaza() {
        ClimaService servicio = servicio((lat, lon, fecha) -> dia(0, 10, 15));

        ApiException e = assertThrows(ApiException.class,
                () -> servicio.consultar(-33.41, -70.66, hoy().minusDays(1), true));

        assertEquals(HttpStatus.BAD_REQUEST, e.getEstado());
    }

    @Test
    void HU31_una_fecha_mas_alla_de_15_dias_se_rechaza() {
        ClimaService servicio = servicio((lat, lon, fecha) -> dia(0, 10, 15));

        ApiException e = assertThrows(ApiException.class,
                () -> servicio.consultar(-33.41, -70.66, hoy().plusDays(16), true));

        assertEquals(HttpStatus.BAD_REQUEST, e.getEstado());
    }

    @Test
    void HU31_la_fecha_es_obligatoria() {
        ClimaService servicio = servicio((lat, lon, fecha) -> dia(0, 10, 15));

        ApiException e = assertThrows(ApiException.class, () -> servicio.consultar(-33.41, -70.66, null, true));

        assertEquals(HttpStatus.BAD_REQUEST, e.getEstado());
    }

    @Test
    void la_misma_consulta_repetida_se_responde_desde_la_memoria_sin_llamar_otra_vez_al_servicio() {
        AtomicInteger llamadas = new AtomicInteger();
        ClimaService servicio = servicio((lat, lon, fecha) -> {
            llamadas.incrementAndGet();
            return dia(0, 10, 15);
        });

        servicio.consultar(-33.41, -70.66, hoy(), true);
        servicio.consultar(-33.41, -70.66, hoy(), true);
        servicio.consultar(-33.41, -70.66, hoy().plusDays(1), true); // otra fecha: consulta nueva

        assertEquals(2, llamadas.get());
    }

    @Test
    void HU31_la_consulta_por_local_usa_las_coordenadas_del_local() {
        Local local = Fabrica.local(10, Fabrica.cliente(1, "Cliente", 4), "Sucursal Independencia");
        when(locales.findById(10L)).thenReturn(Optional.of(local));
        double[] recibidas = new double[2];
        ClimaService servicio = servicio((lat, lon, fecha) -> {
            recibidas[0] = lat;
            recibidas[1] = lon;
            return dia(0, 10, 15);
        });

        servicio.consultarLocal(10L, hoy(), true);

        assertEquals(-33.4150, recibidas[0]);
        assertEquals(-70.6580, recibidas[1]);
    }

    @Test
    void HU31_un_local_que_no_existe_responde_404() {
        when(locales.findById(99L)).thenReturn(Optional.empty());
        ClimaService servicio = servicio((lat, lon, fecha) -> dia(0, 10, 15));

        ApiException e = assertThrows(ApiException.class, () -> servicio.consultarLocal(99L, hoy(), true));

        assertEquals(HttpStatus.NOT_FOUND, e.getEstado());
    }
}
