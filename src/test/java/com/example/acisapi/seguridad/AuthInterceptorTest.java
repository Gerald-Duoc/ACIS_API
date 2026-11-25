package com.example.acisapi.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.acisapi.Fabrica;
import com.example.acisapi.error.ApiException;
import com.example.acisapi.modelo.Perfil;
import com.example.acisapi.modelo.Usuario;
import com.example.acisapi.servicio.AuthService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

/** HU-01: cada perfil usa solo lo suyo. Revisa el token y los perfiles permitidos. */
class AuthInterceptorTest {

    /** Controlador de mentira con tres tipos de endpoint. */
    static class ControladorDePrueba {
        @Publico
        public void abierto() {
        }

        public void soloConSesion() {
        }

        @RequierePerfil(Perfil.PLANIFICADOR)
        public void soloPlanificador() {
        }
    }

    @RequierePerfil(Perfil.CLIENTE)
    static class ControladorDeClientes {
        public void ver() {
        }
    }

    private final AuthService auth = mock(AuthService.class);
    private final AuthInterceptor interceptor = new AuthInterceptor(auth);
    private final Usuario tecnico = Fabrica.usuario(1, "Carlos", "tecnico@acis.cl", Perfil.TECNICO);
    private final Usuario planificador = Fabrica.usuario(2, "Plani", "planificador@acis.cl", Perfil.PLANIFICADOR);
    private final Usuario cliente = Fabrica.usuario(3, "Ana", "cliente@acis.cl", Perfil.CLIENTE);

    private HandlerMethod metodo(Object bean, String nombre) throws Exception {
        return new HandlerMethod(bean, bean.getClass().getMethod(nombre));
    }

    private MockHttpServletRequest conToken(String token) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.addHeader("Authorization", "Bearer " + token);
        return r;
    }

    private HttpStatus estadoDelError(MockHttpServletRequest request, HandlerMethod destino) {
        ApiException e = assertThrows(ApiException.class,
                () -> interceptor.preHandle(request, new MockHttpServletResponse(), destino));
        return e.getEstado();
    }

    @Test
    void un_endpoint_publico_se_puede_usar_sin_sesion() throws Exception {
        boolean sigue = interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(),
                metodo(new ControladorDePrueba(), "abierto"));

        assertTrue(sigue);
    }

    @Test
    void sin_token_se_responde_401() throws Exception {
        HttpStatus estado = estadoDelError(new MockHttpServletRequest(), metodo(new ControladorDePrueba(), "soloConSesion"));

        assertEquals(HttpStatus.UNAUTHORIZED, estado);
    }

    @Test
    void un_encabezado_sin_la_palabra_Bearer_se_trata_como_sin_token() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "abc123");

        assertEquals(HttpStatus.UNAUTHORIZED, estadoDelError(request, metodo(new ControladorDePrueba(), "soloConSesion")));
    }

    @Test
    void un_token_invalido_o_vencido_responde_401() throws Exception {
        when(auth.usuarioDeToken("falso")).thenReturn(Optional.empty());

        HttpStatus estado = estadoDelError(conToken("falso"), metodo(new ControladorDePrueba(), "soloConSesion"));

        assertEquals(HttpStatus.UNAUTHORIZED, estado);
    }

    @Test
    void con_un_token_valido_el_usuario_queda_disponible_para_el_controlador() throws Exception {
        when(auth.usuarioDeToken("tok")).thenReturn(Optional.of(tecnico));
        MockHttpServletRequest request = conToken("tok");

        boolean sigue = interceptor.preHandle(request, new MockHttpServletResponse(),
                metodo(new ControladorDePrueba(), "soloConSesion"));

        assertTrue(sigue);
        assertEquals(tecnico, request.getAttribute(AuthInterceptor.ATRIBUTO_USUARIO));
    }

    @Test
    void el_perfil_permitido_puede_entrar() throws Exception {
        when(auth.usuarioDeToken("tok")).thenReturn(Optional.of(planificador));

        boolean sigue = interceptor.preHandle(conToken("tok"), new MockHttpServletResponse(),
                metodo(new ControladorDePrueba(), "soloPlanificador"));

        assertTrue(sigue);
    }

    @Test
    void un_perfil_no_permitido_recibe_403() throws Exception {
        when(auth.usuarioDeToken("tok")).thenReturn(Optional.of(tecnico));

        HttpStatus estado = estadoDelError(conToken("tok"), metodo(new ControladorDePrueba(), "soloPlanificador"));

        assertEquals(HttpStatus.FORBIDDEN, estado);
    }

    @Test
    void la_restriccion_tambien_funciona_a_nivel_de_toda_la_clase() throws Exception {
        when(auth.usuarioDeToken("tecnico")).thenReturn(Optional.of(tecnico));
        when(auth.usuarioDeToken("cliente")).thenReturn(Optional.of(cliente));

        assertEquals(HttpStatus.FORBIDDEN, estadoDelError(conToken("tecnico"), metodo(new ControladorDeClientes(), "ver")));
        assertTrue(interceptor.preHandle(conToken("cliente"), new MockHttpServletResponse(),
                metodo(new ControladorDeClientes(), "ver")));
    }

    @Test
    void lo_que_no_es_un_endpoint_no_se_revisa() {
        boolean sigue = interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), "recurso-estatico");

        assertTrue(sigue);
    }

    @Test
    void el_token_se_extrae_del_encabezado_Authorization() {
        assertEquals("abc123", AuthInterceptor.extraerToken("Bearer abc123"));
        assertEquals("abc123", AuthInterceptor.extraerToken("Bearer   abc123  "));
        assertEquals("", AuthInterceptor.extraerToken(null));
        assertEquals("", AuthInterceptor.extraerToken("Basic abc123"));
    }
}
