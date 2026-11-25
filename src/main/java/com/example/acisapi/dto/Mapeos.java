package com.example.acisapi.dto;

import com.example.acisapi.modelo.Cliente;
import com.example.acisapi.modelo.Equipo;
import com.example.acisapi.modelo.Local;
import com.example.acisapi.modelo.Usuario;
import java.util.List;

/** Convierte las entidades de la base de datos en los objetos que se envían por la API. */
public final class Mapeos {

    private Mapeos() {
    }

    public static Respuestas.UsuarioDto usuario(Usuario u) {
        return new Respuestas.UsuarioDto(
                u.getId(),
                u.getNombre(),
                u.getCorreo(),
                u.getPerfil(),
                u.getCliente() == null ? null : u.getCliente().getId(),
                u.getLocal() == null ? null : u.getLocal().getId());
    }

    public static Respuestas.EquipoDto equipo(Equipo e) {
        return new Respuestas.EquipoDto(
                e.getId(),
                e.getLocal().getId(),
                e.getNombre(),
                e.getTipo(),
                e.getMarca(),
                e.getModelo(),
                e.isExterior(),
                e.getUbicacion(),
                e.getUltimaMantencion());
    }

    public static Respuestas.LocalDto local(Local l, List<Equipo> equipos) {
        return new Respuestas.LocalDto(
                l.getId(),
                l.getCliente().getId(),
                l.getNombre(),
                l.getDireccion(),
                l.getLatitud(),
                l.getLongitud(),
                equipos.stream().map(Mapeos::equipo).toList());
    }

    public static Respuestas.ClienteDto cliente(Cliente c, List<Respuestas.LocalDto> locales) {
        return new Respuestas.ClienteDto(c.getId(), c.getNombre(), c.getContacto(), c.getSlaHoras(), locales);
    }
}
