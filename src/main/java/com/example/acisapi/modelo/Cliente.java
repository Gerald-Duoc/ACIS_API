package com.example.acisapi.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Empresa cliente de ACIS (HU-32). El SLA se define en horas por cliente (HU-36). */
@Entity
@Table(name = "clientes")
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 160)
    private String nombre;

    @Column(nullable = false, length = 200)
    private String contacto;

    @Column(name = "sla_horas", nullable = false)
    private Integer slaHoras;

    protected Cliente() {
    }

    public Cliente(String nombre, String contacto, Integer slaHoras) {
        this.nombre = nombre;
        this.contacto = contacto;
        this.slaHoras = slaHoras;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getContacto() { return contacto; }
    public void setContacto(String contacto) { this.contacto = contacto; }
    public Integer getSlaHoras() { return slaHoras; }
    public void setSlaHoras(Integer slaHoras) { this.slaHoras = slaHoras; }
}
