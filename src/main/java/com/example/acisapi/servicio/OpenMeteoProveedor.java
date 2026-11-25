package com.example.acisapi.servicio;

import com.example.acisapi.error.ApiException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Consulta el clima real en Open-Meteo (https://open-meteo.com): gratis, sin clave y para
 * uso no comercial. Su licencia pide dar atribución, por eso cada respuesta de la API
 * incluye el campo "fuente".
 */
@Component
public class OpenMeteoProveedor implements ProveedorClima {

    private final RestClient cliente;
    private final String zonaHoraria;

    public OpenMeteoProveedor(@Value("${acis.clima.url:https://api.open-meteo.com}") String url,
                              @Value("${acis.clima.zona-horaria:America/Santiago}") String zonaHoraria) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(http);
        fabrica.setReadTimeout(Duration.ofSeconds(8));
        this.cliente = RestClient.builder().baseUrl(url).requestFactory(fabrica).build();
        this.zonaHoraria = zonaHoraria;
    }

    @Override
    @SuppressWarnings("unchecked")
    public PronosticoDia pronostico(double latitud, double longitud, LocalDate fecha) {
        Map<String, Object> cuerpo;
        try {
            cuerpo = cliente.get()
                    .uri(u -> u.path("/v1/forecast")
                            .queryParam("latitude", latitud)
                            .queryParam("longitude", longitud)
                            .queryParam("daily", "temperature_2m_max,precipitation_sum,"
                                    + "precipitation_probability_max,wind_speed_10m_max,wind_gusts_10m_max")
                            .queryParam("timezone", zonaHoraria)
                            .queryParam("start_date", fecha.toString())
                            .queryParam("end_date", fecha.toString())
                            .build())
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientResponseException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "El servicio del clima respondió con un error (código " + e.getStatusCode().value() + ").");
        } catch (RestClientException e) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo consultar el servicio del clima. Revisa la conexión a internet del servidor.");
        }

        if (cuerpo == null || !(cuerpo.get("daily") instanceof Map<?, ?> diario)) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "El servicio del clima devolvió una respuesta inesperada.");
        }
        return new PronosticoDia(
                fecha,
                numero(diario, "temperature_2m_max"),
                numero(diario, "precipitation_sum"),
                (int) Math.round(numero(diario, "precipitation_probability_max")),
                numero(diario, "wind_speed_10m_max"),
                numero(diario, "wind_gusts_10m_max"));
    }

    /** Lee el primer valor de una lista del JSON; si falta o es nulo devuelve 0. */
    private static double numero(Map<?, ?> diario, String clave) {
        Object valor = diario.get(clave);
        if (valor instanceof List<?> lista && !lista.isEmpty() && lista.get(0) instanceof Number n) {
            return n.doubleValue();
        }
        return 0.0;
    }
}
