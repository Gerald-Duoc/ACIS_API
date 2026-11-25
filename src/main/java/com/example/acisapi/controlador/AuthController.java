package com.example.acisapi.controlador;

import static com.example.acisapi.seguridad.AuthInterceptor.ATRIBUTO_USUARIO;

import com.example.acisapi.dto.Mapeos;
import com.example.acisapi.dto.Peticiones.LoginRequest;
import com.example.acisapi.dto.Respuestas.LoginResponse;
import com.example.acisapi.dto.Respuestas.SaludDto;
import com.example.acisapi.dto.Respuestas.UsuarioDto;
import com.example.acisapi.modelo.Usuario;
import com.example.acisapi.seguridad.Publico;
import com.example.acisapi.servicio.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Acceso a la aplicación (HU-01) y comprobación de que la API está viva. */
@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    /** Sin sesión: sirve para probar desde el celular que la API responde. */
    @Publico
    @GetMapping("/salud")
    public SaludDto salud() {
        return new SaludDto("ok", LocalDateTime.now());
    }

    @Publico
    @PostMapping("/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest peticion) {
        return auth.login(peticion.correo(), peticion.clave());
    }

    @PostMapping("/auth/logout")
    public void logout(HttpServletRequest request) {
        String cabecera = request.getHeader("Authorization");
        if (cabecera != null && cabecera.startsWith("Bearer ")) {
            auth.logout(cabecera.substring("Bearer ".length()).trim());
        }
    }

    /** Datos del usuario que tiene la sesión abierta. */
    @GetMapping("/auth/me")
    public UsuarioDto yo(@RequestAttribute(ATRIBUTO_USUARIO) Usuario usuario) {
        return Mapeos.usuario(usuario);
    }
}
