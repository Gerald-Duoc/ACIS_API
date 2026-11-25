package com.example.acisapi.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.acisapi.dto.Respuestas.LoginResponse;
import com.example.acisapi.error.ApiException;
import com.example.acisapi.modelo.Perfil;
import com.example.acisapi.modelo.Sesion;
import com.example.acisapi.modelo.Usuario;
import com.example.acisapi.repositorio.SesionRepository;
import com.example.acisapi.repositorio.UsuarioRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/** HU-01: iniciar sesión validando contra la API. Usa BCrypt real para comprobar las claves. */
class AuthServiceTest {

    private final UsuarioRepository usuarios = mock(UsuarioRepository.class);
    private final SesionRepository sesiones = mock(SesionRepository.class);
    private final PasswordEncoder codificador = new BCryptPasswordEncoder();
    private final AuthService servicio = new AuthService(usuarios, sesiones, codificador, 12);

    private Usuario carlos;

    @BeforeEach
    void preparar() {
        // la clave "1234" guardada cifrada, como en la base de datos real
        carlos = new Usuario("Carlos Muñoz", "tecnico@acis.cl", codificador.encode("1234"), Perfil.TECNICO);
        ReflectionTestUtils.setField(carlos, "id", 1L);
        when(usuarios.findByCorreoIgnoreCase("tecnico@acis.cl")).thenReturn(Optional.of(carlos));
        when(sesiones.save(any(Sesion.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void HU01_con_correo_y_clave_correctos_se_entrega_un_token_y_el_perfil() {
        LoginResponse r = servicio.login("tecnico@acis.cl", "1234");

        assertEquals(Perfil.TECNICO, r.usuario().perfil());
        assertEquals("Carlos Muñoz", r.usuario().nombre());
        assertFalse(r.token().isBlank());
        assertTrue(r.expiraEn().isAfter(LocalDateTime.now().plusHours(11)));
    }

    @Test
    void HU01_el_correo_se_reconoce_con_espacios() {
        LoginResponse r = servicio.login("  tecnico@acis.cl  ", "1234");

        assertEquals("tecnico@acis.cl", r.usuario().correo());
    }

    @Test
    void HU01_una_clave_incorrecta_responde_401_con_mensaje_de_error() {
        ApiException e = assertThrows(ApiException.class, () -> servicio.login("tecnico@acis.cl", "mala"));

        assertEquals(HttpStatus.UNAUTHORIZED, e.getEstado());
        assertEquals("Correo o clave incorrectos. Inténtalo nuevamente.", e.getMessage());
    }

    @Test
    void HU01_un_correo_que_no_existe_da_exactamente_el_mismo_mensaje_que_una_clave_mala() {
        when(usuarios.findByCorreoIgnoreCase("nadie@acis.cl")).thenReturn(Optional.empty());

        ApiException sinCorreo = assertThrows(ApiException.class, () -> servicio.login("nadie@acis.cl", "1234"));
        ApiException claveMala = assertThrows(ApiException.class, () -> servicio.login("tecnico@acis.cl", "mala"));

        assertEquals(HttpStatus.UNAUTHORIZED, sinCorreo.getEstado());
        assertEquals(claveMala.getMessage(), sinCorreo.getMessage());
    }

    @Test
    void la_clave_se_guarda_cifrada_y_no_en_texto_plano() {
        assertNotEquals("1234", carlos.getClaveHash());
        assertTrue(carlos.getClaveHash().startsWith("$2"));
    }

    @Test
    void cada_inicio_de_sesion_genera_un_token_distinto() {
        String a = servicio.login("tecnico@acis.cl", "1234").token();
        String b = servicio.login("tecnico@acis.cl", "1234").token();

        assertNotEquals(a, b);
    }

    @Test
    void con_un_token_vigente_se_obtiene_el_usuario() {
        Sesion vigente = new Sesion("tok", carlos, LocalDateTime.now(), LocalDateTime.now().plusHours(1));
        when(sesiones.findByToken("tok")).thenReturn(Optional.of(vigente));

        assertEquals(carlos, servicio.usuarioDeToken("tok").orElseThrow());
    }

    @Test
    void con_un_token_vencido_no_se_obtiene_usuario() {
        Sesion vencida = new Sesion("tok", carlos, LocalDateTime.now().minusHours(13), LocalDateTime.now().minusHours(1));
        when(sesiones.findByToken("tok")).thenReturn(Optional.of(vencida));

        assertTrue(servicio.usuarioDeToken("tok").isEmpty());
    }

    @Test
    void un_token_que_no_existe_no_entrega_usuario() {
        when(sesiones.findByToken("falso")).thenReturn(Optional.empty());

        assertTrue(servicio.usuarioDeToken("falso").isEmpty());
        assertTrue(servicio.usuarioDeToken("").isEmpty());
    }

    @Test
    void cerrar_sesion_elimina_la_sesion() {
        Sesion s = new Sesion("tok", carlos, LocalDateTime.now(), LocalDateTime.now().plusHours(1));
        when(sesiones.findByToken("tok")).thenReturn(Optional.of(s));

        servicio.logout("tok");

        verify(sesiones).delete(s);
    }

    @Test
    void cerrar_sesion_con_un_token_desconocido_no_falla() {
        when(sesiones.findByToken("falso")).thenReturn(Optional.empty());

        servicio.logout("falso");

        verify(sesiones, never()).delete(any(Sesion.class));
    }
}
