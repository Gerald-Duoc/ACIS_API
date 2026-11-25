package com.example.acisapi.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Persona que usa la aplicación: técnico, planificador o cliente (HU-01, HU-02).
 * La clave se guarda cifrada con BCrypt, nunca en texto plano.
 * Los usuarios con perfil CLIENTE quedan asociados a su cliente y a su local.
 */
@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false, unique = true, length = 160)
    private String correo;

    @Column(name = "clave_hash", nullable = false, length = 100)
    private String claveHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Perfil perfil;

    @ManyToOne
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @ManyToOne
    @JoinColumn(name = "local_id")
    private Local local;

    protected Usuario() {
    }

    public Usuario(String nombre, String correo, String claveHash, Perfil perfil) {
        this.nombre = nombre;
        this.correo = correo == null ? null : correo.trim().toLowerCase();
        this.claveHash = claveHash;
        this.perfil = perfil;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getCorreo() { return correo; }
    public String getClaveHash() { return claveHash; }
    public Perfil getPerfil() { return perfil; }
    public Cliente getCliente() { return cliente; }
    public Local getLocal() { return local; }

    /** Asocia al usuario con el local donde trabaja (solo para perfil CLIENTE). */
    public void asociarALocal(Local local) {
        this.local = local;
        this.cliente = local == null ? null : local.getCliente();
    }
}
