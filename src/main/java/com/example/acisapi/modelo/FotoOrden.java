package com.example.acisapi.modelo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Fotografía de una OT. Por indicación del profesor, la imagen se guarda
 * dentro de la base de datos (columna LONGBLOB), no en una carpeta.
 */
@Entity
@Table(name = "fotos")
public class FotoOrden {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "orden_id")
    private OrdenTrabajo orden;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoFoto tipo;

    @Column(name = "nombre_archivo", nullable = false, length = 150)
    private String nombreArchivo;

    @Column(name = "content_type", nullable = false, length = 60)
    private String contentType;

    @Column(nullable = false)
    private Long tamano;

    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGBLOB")
    private byte[] datos;

    protected FotoOrden() {
    }

    public FotoOrden(OrdenTrabajo orden, TipoFoto tipo, String nombreArchivo, String contentType,
                     Long tamano, LocalDateTime creadaEn, byte[] datos) {
        this.orden = orden;
        this.tipo = tipo;
        this.nombreArchivo = nombreArchivo;
        this.contentType = contentType;
        this.tamano = tamano;
        this.creadaEn = creadaEn;
        this.datos = datos;
    }

    public Long getId() { return id; }
    public OrdenTrabajo getOrden() { return orden; }
    public TipoFoto getTipo() { return tipo; }
    public String getNombreArchivo() { return nombreArchivo; }
    public String getContentType() { return contentType; }
    public Long getTamano() { return tamano; }
    public LocalDateTime getCreadaEn() { return creadaEn; }
    public byte[] getDatos() { return datos; }
}
