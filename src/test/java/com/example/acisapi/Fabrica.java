package com.example.acisapi;

import com.example.acisapi.modelo.Cliente;
import com.example.acisapi.modelo.Equipo;
import com.example.acisapi.modelo.EstadoOt;
import com.example.acisapi.modelo.Local;
import com.example.acisapi.modelo.OrdenTrabajo;
import com.example.acisapi.modelo.Perfil;
import com.example.acisapi.modelo.Prioridad;
import com.example.acisapi.modelo.Usuario;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Arma objetos de prueba en memoria (sin base de datos).
 * Como los ids los genera la base de datos, aquí se asignan a mano con reflexión.
 */
public final class Fabrica {

    private Fabrica() {
    }

    public static Usuario usuario(long id, String nombre, String correo, Perfil perfil) {
        Usuario u = new Usuario(nombre, correo, "hash", perfil);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    public static Cliente cliente(long id, String nombre, int slaHoras) {
        Cliente c = new Cliente(nombre, "Contacto de prueba", slaHoras);
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    public static Local local(long id, Cliente cliente, String nombre) {
        Local l = new Local(cliente, nombre, "Dirección de prueba 123", -33.4150, -70.6580);
        ReflectionTestUtils.setField(l, "id", id);
        return l;
    }

    public static Equipo equipo(long id, Local local, String nombre, boolean exterior) {
        Equipo e = new Equipo(local, nombre, "Condensadora", "FrioMax", "CX-450", exterior,
                exterior ? "Azotea" : "Bodega", LocalDate.now().minusDays(60));
        ReflectionTestUtils.setField(e, "id", id);
        return e;
    }

    /** OT de prueba agendada para mañana. */
    public static OrdenTrabajo orden(long id, int numero, Equipo equipo, EstadoOt estado, Usuario tecnico) {
        OrdenTrabajo o = new OrdenTrabajo();
        ReflectionTestUtils.setField(o, "id", id);
        o.setNumero(numero);
        o.setEquipo(equipo);
        o.setTipo("Correctiva");
        o.setPrioridad(Prioridad.MEDIA);
        o.setEstado(estado);
        o.setTecnico(tecnico);
        o.setFecha(LocalDate.now().plusDays(1));
        o.setDescripcion("Falla de prueba");
        o.setSlaHoras(equipo.getLocal().getCliente().getSlaHoras());
        o.setCreadaEn(LocalDateTime.now());
        return o;
    }
}
