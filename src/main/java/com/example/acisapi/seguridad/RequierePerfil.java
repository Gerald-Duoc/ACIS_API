package com.example.acisapi.seguridad;

import com.example.acisapi.modelo.Perfil;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Limita un endpoint a ciertos perfiles. Ejemplo: @RequierePerfil(Perfil.PLANIFICADOR) */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequierePerfil {

    Perfil[] value();
}
