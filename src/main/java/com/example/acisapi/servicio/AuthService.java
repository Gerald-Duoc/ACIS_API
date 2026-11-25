package com.example.acisapi.servicio;

import com.example.acisapi.dto.Mapeos;
import com.example.acisapi.dto.Respuestas.LoginResponse;
import com.example.acisapi.error.ApiException;
import com.example.acisapi.modelo.Sesion;
import com.example.acisapi.modelo.Usuario;
import com.example.acisapi.repositorio.SesionRepository;
import com.example.acisapi.repositorio.UsuarioRepository;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** HU-01: valida el correo y la clave y entrega un token para las siguientes peticiones. */
@Service
public class AuthService {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final UsuarioRepository usuarios;
    private final SesionRepository sesiones;
    private final PasswordEncoder codificador;
    private final long horasSesion;

    public AuthService(UsuarioRepository usuarios,
                       SesionRepository sesiones,
                       PasswordEncoder codificador,
                       @Value("${acis.sesion.horas:12}") long horasSesion) {
        this.usuarios = usuarios;
        this.sesiones = sesiones;
        this.codificador = codificador;
        this.horasSesion = horasSesion;
    }

    @Transactional
    public LoginResponse login(String correo, String clave) {
        Usuario usuario = usuarios.findByCorreoIgnoreCase(correo.trim())
                .orElseThrow(AuthService::credencialesInvalidas);
        if (!codificador.matches(clave, usuario.getClaveHash())) {
            throw credencialesInvalidas();
        }
        LocalDateTime ahora = LocalDateTime.now();
        sesiones.deleteByExpiraEnBefore(ahora); // limpia sesiones vencidas
        Sesion sesion = new Sesion(nuevoToken(), usuario, ahora, ahora.plusHours(horasSesion));
        sesiones.save(sesion);
        return new LoginResponse(sesion.getToken(), sesion.getExpiraEn(), Mapeos.usuario(usuario));
    }

    @Transactional
    public void logout(String token) {
        sesiones.findByToken(token).ifPresent(sesiones::delete);
    }

    /** Devuelve el usuario dueño del token, o vacío si no existe o ya expiró. */
    @Transactional(readOnly = true)
    public Optional<Usuario> usuarioDeToken(String token) {
        return sesiones.findByToken(token)
                .filter(s -> s.getExpiraEn().isAfter(LocalDateTime.now()))
                .map(Sesion::getUsuario);
    }

    private static ApiException credencialesInvalidas() {
        // Mismo mensaje si el correo no existe o la clave está mala (no se revela cuál falló)
        return new ApiException(HttpStatus.UNAUTHORIZED, "Correo o clave incorrectos. Inténtalo nuevamente.");
    }

    private static String nuevoToken() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
