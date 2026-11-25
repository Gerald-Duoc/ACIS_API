package com.example.acisapi.seguridad;

import com.example.acisapi.error.ApiException;
import com.example.acisapi.modelo.Usuario;
import com.example.acisapi.servicio.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Se ejecuta antes de cada endpoint de /api:
 * 1) exige el token (Authorization: Bearer ...), salvo en los marcados con @Publico;
 * 2) deja al usuario disponible para el controlador;
 * 3) revisa @RequierePerfil (qué perfiles pueden usar el endpoint).
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /** Nombre con el que el usuario queda guardado en la petición. */
    public static final String ATRIBUTO_USUARIO = "usuarioActual";

    private final AuthService auth;

    public AuthInterceptor(AuthService auth) {
        this.auth = auth;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod metodo)) {
            return true;
        }
        if (metodo.hasMethodAnnotation(Publico.class)) {
            return true;
        }

        String token = extraerToken(request.getHeader("Authorization"));
        Usuario usuario = auth.usuarioDeToken(token)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED,
                        "Debes iniciar sesión. Si ya lo hiciste, tu sesión expiró."));
        request.setAttribute(ATRIBUTO_USUARIO, usuario);

        RequierePerfil requisito = metodo.getMethodAnnotation(RequierePerfil.class);
        if (requisito == null) {
            requisito = metodo.getBeanType().getAnnotation(RequierePerfil.class);
        }
        if (requisito != null && !Arrays.asList(requisito.value()).contains(usuario.getPerfil())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Tu perfil no tiene permiso para esta acción.");
        }
        return true;
    }

    /** Saca el token de "Bearer abc123". Devuelve "" si no viene. */
    static String extraerToken(String cabecera) {
        if (cabecera == null || !cabecera.startsWith("Bearer ")) {
            return "";
        }
        return cabecera.substring("Bearer ".length()).trim();
    }
}
