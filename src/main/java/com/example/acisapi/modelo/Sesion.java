package com.example.acisapi.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Sesión abierta después de iniciar sesión: el token viaja en cada petición (Authorization: Bearer ...). */
@Entity
@Table(name = "sesiones")
public class Sesion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String token;

    @ManyToOne(optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn;

    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    protected Sesion() {
    }

    public Sesion(String token, Usuario usuario, LocalDateTime creadaEn, LocalDateTime expiraEn) {
        this.token = token;
        this.usuario = usuario;
        this.creadaEn = creadaEn;
        this.expiraEn = expiraEn;
    }

    public Long getId() { return id; }
    public String getToken() { return token; }
    public Usuario getUsuario() { return usuario; }
    public LocalDateTime getCreadaEn() { return creadaEn; }
    public LocalDateTime getExpiraEn() { return expiraEn; }
}
