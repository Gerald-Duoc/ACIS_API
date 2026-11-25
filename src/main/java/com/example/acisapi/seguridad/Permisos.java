package com.example.acisapi.seguridad;

import com.example.acisapi.modelo.OrdenTrabajo;
import com.example.acisapi.modelo.Usuario;

/**
 * Reglas de qué OT puede ver cada perfil:
 * - el planificador ve todas;
 * - el técnico ve solo las que tiene asignadas (HU-01);
 * - el cliente ve solo las de su local (HU-41).
 */
public final class Permisos {

    private Permisos() {
    }

    public static boolean puedeVer(Usuario usuario, OrdenTrabajo orden) {
        return switch (usuario.getPerfil()) {
            case PLANIFICADOR -> true;
            case TECNICO -> esTecnicoAsignado(usuario, orden);
            case CLIENTE -> usuario.getLocal() != null
                    && orden.getEquipo().getLocal().getId().equals(usuario.getLocal().getId());
        };
    }

    public static boolean esTecnicoAsignado(Usuario usuario, OrdenTrabajo orden) {
        return orden.getTecnico() != null && orden.getTecnico().getId().equals(usuario.getId());
    }
}
