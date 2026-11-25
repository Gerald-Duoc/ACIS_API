package com.example.acisapi.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;

/** Equipo de climatización o refrigeración instalado en un local (HU-34). */
@Entity
@Table(name = "equipos")
public class Equipo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "local_id")
    private Local local;

    @Column(nullable = false, length = 160)
    private String nombre;

    @Column(nullable = false, length = 80)
    private String tipo;

    @Column(nullable = false, length = 80)
    private String marca;

    @Column(nullable = false, length = 80)
    private String modelo;

    /** true si está en azotea o patio: es sensible a la lluvia y al viento (HU-29, HU-30, HU-31). */
    @Column(nullable = false)
    private boolean exterior;

    @Column(length = 80)
    private String ubicacion;

    @Column(name = "ultima_mantencion")
    private LocalDate ultimaMantencion;

    protected Equipo() {
    }

    public Equipo(Local local, String nombre, String tipo, String marca, String modelo,
                  boolean exterior, String ubicacion, LocalDate ultimaMantencion) {
        this.local = local;
        this.nombre = nombre;
        this.tipo = tipo;
        this.marca = marca;
        this.modelo = modelo;
        this.exterior = exterior;
        this.ubicacion = ubicacion;
        this.ultimaMantencion = ultimaMantencion;
    }

    public Long getId() { return id; }
    public Local getLocal() { return local; }
    public String getNombre() { return nombre; }
    public String getTipo() { return tipo; }
    public String getMarca() { return marca; }
    public String getModelo() { return modelo; }
    public boolean isExterior() { return exterior; }
    public String getUbicacion() { return ubicacion; }
    public LocalDate getUltimaMantencion() { return ultimaMantencion; }
    public void setUltimaMantencion(LocalDate ultimaMantencion) { this.ultimaMantencion = ultimaMantencion; }
}
