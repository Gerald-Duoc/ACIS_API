package com.example.acisapi.modelo;

/** Ciclo de vida de una orden de trabajo (mismos nombres que la app Android). */
public enum EstadoOt {
    CREADA("Creada"),
    PENDIENTE_ASIGNACION("Pendiente de asignación"),
    ASIGNADA("Asignada"),
    ACEPTADA("Aceptada"),
    EN_EJECUCION("En ejecución"),
    PENDIENTE_IMPREVISTO("Pendiente por imprevisto"),
    CERRADA("Cerrada");

    private final String etiqueta;

    EstadoOt(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }
}
