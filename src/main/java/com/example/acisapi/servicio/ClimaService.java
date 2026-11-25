package com.example.acisapi.servicio;

import com.example.acisapi.dto.Respuestas.ClimaDto;
import com.example.acisapi.error.ApiException;
import com.example.acisapi.modelo.Clima;
import com.example.acisapi.modelo.Local;
import com.example.acisapi.repositorio.LocalRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * HU-29, HU-30 y HU-31: entrega el pronóstico real de un lugar y lo clasifica como
 * DESPEJADO, LLUVIA o VIENTO_FUERTE (los mismos valores que usa la app).
 *
 * Reglas (se pueden cambiar en application.properties):
 * - LLUVIA: precipitación del día de 1 mm o más.
 * - VIENTO_FUERTE: viento máximo de 40 km/h o más, o ráfagas de 60 km/h o más.
 * - Hay alerta solo si el equipo está en el exterior y el clima no es DESPEJADO.
 */
@Service
public class ClimaService {

    public static final String FUENTE = "Datos del clima por Open-Meteo.com (CC BY 4.0)";
    private static final long MINUTOS_CACHE = 30;
    /** Open-Meteo entrega pronóstico de hoy hasta 15 días más. */
    private static final int DIAS_MAXIMOS = 15;

    private final ProveedorClima proveedor;
    private final LocalRepository locales;
    private final double umbralLluviaMm;
    private final double umbralVientoKmh;
    private final ZoneId zona;
    private final Map<String, Cacheado> cache = new ConcurrentHashMap<>();

    private record Cacheado(PronosticoDia pronostico, Instant guardadoEn) {
    }

    public ClimaService(ProveedorClima proveedor,
                        LocalRepository locales,
                        @Value("${acis.clima.umbral-lluvia-mm:1.0}") double umbralLluviaMm,
                        @Value("${acis.clima.umbral-viento-kmh:40}") double umbralVientoKmh,
                        @Value("${acis.clima.zona-horaria:America/Santiago}") String zona) {
        this.proveedor = proveedor;
        this.locales = locales;
        this.umbralLluviaMm = umbralLluviaMm;
        this.umbralVientoKmh = umbralVientoKmh;
        this.zona = ZoneId.of(zona);
    }

    /** Pronóstico para unas coordenadas. [exterior] indica si el equipo está afuera (para la alerta). */
    public ClimaDto consultar(double latitud, double longitud, LocalDate fecha, boolean exterior) {
        validarFecha(fecha);
        PronosticoDia pronostico = obtener(latitud, longitud, fecha);
        return armar(pronostico, exterior);
    }

    /** Pronóstico para un local (HU-31: advertencia al agendar). */
    public ClimaDto consultarLocal(Long localId, LocalDate fecha, boolean exterior) {
        Local local = locales.findById(localId)
                .orElseThrow(() -> ApiException.noEncontrado("El local no existe."));
        return consultar(local.getLatitud(), local.getLongitud(), fecha, exterior);
    }

    // ------------------------------------------------------------------
    // Reglas de clasificación (públicas y estáticas para poder probarlas)
    // ------------------------------------------------------------------

    public static boolean hayLluvia(PronosticoDia p, double umbralMm) {
        return p.precipitacionMm() >= umbralMm;
    }

    public static boolean hayVientoFuerte(PronosticoDia p, double umbralKmh) {
        return p.vientoMaxKmh() >= umbralKmh || p.rafagaMaxKmh() >= umbralKmh * 1.5;
    }

    public static Clima clasificar(PronosticoDia p, double umbralLluviaMm, double umbralVientoKmh) {
        if (hayLluvia(p, umbralLluviaMm)) {
            return Clima.LLUVIA;
        }
        if (hayVientoFuerte(p, umbralVientoKmh)) {
            return Clima.VIENTO_FUERTE;
        }
        return Clima.DESPEJADO;
    }

    /** Misma regla de la app: alerta si el equipo está afuera y no hay cielo despejado. */
    public static boolean requiereAlerta(boolean exterior, Clima clima) {
        return exterior && clima != Clima.DESPEJADO;
    }

    // ------------------------------------------------------------------

    private ClimaDto armar(PronosticoDia p, boolean exterior) {
        Clima clima = clasificar(p, umbralLluviaMm, umbralVientoKmh);
        return new ClimaDto(
                p.fecha(),
                clima,
                hayLluvia(p, umbralLluviaMm),
                hayVientoFuerte(p, umbralVientoKmh),
                requiereAlerta(exterior, clima),
                p.temperaturaMaxC(),
                p.precipitacionMm(),
                p.probabilidadLluviaPct(),
                p.vientoMaxKmh(),
                p.rafagaMaxKmh(),
                FUENTE);
    }

    private void validarFecha(LocalDate fecha) {
        LocalDate hoy = LocalDate.now(zona);
        if (fecha == null) {
            throw ApiException.invalido("Debes indicar la fecha (formato AAAA-MM-DD).");
        }
        if (fecha.isBefore(hoy) || fecha.isAfter(hoy.plusDays(DIAS_MAXIMOS))) {
            throw ApiException.invalido("El pronóstico está disponible desde hoy (" + hoy
                    + ") hasta " + DIAS_MAXIMOS + " días después.");
        }
    }

    private PronosticoDia obtener(double latitud, double longitud, LocalDate fecha) {
        String clave = String.format(Locale.ROOT, "%.3f,%.3f,%s", latitud, longitud, fecha);
        Cacheado guardado = cache.get(clave);
        if (guardado != null && guardado.guardadoEn().plusSeconds(MINUTOS_CACHE * 60).isAfter(Instant.now())) {
            return guardado.pronostico();
        }
        PronosticoDia nuevo = proveedor.pronostico(latitud, longitud, fecha);
        cache.put(clave, new Cacheado(nuevo, Instant.now()));
        return nuevo;
    }
}
