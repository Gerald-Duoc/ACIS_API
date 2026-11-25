package com.example.acisapi.modelo;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Orden de trabajo (OT). Guarda todo su ciclo de vida: creación, asignación, aceptación,
 * ejecución en terreno (check-in, diagnóstico, actividades, materiales, check-out),
 * cierre y conformidad del cliente.
 */
@Entity
@Table(name = "ordenes")
public class OrdenTrabajo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Número visible: OT-1041 es el número 1041. */
    @Column(nullable = false, unique = true)
    private Integer numero;

    @ManyToOne(optional = false)
    @JoinColumn(name = "equipo_id")
    private Equipo equipo;

    /** "Preventiva" o "Correctiva". */
    @Column(nullable = false, length = 20)
    private String tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Prioridad prioridad;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoOt estado;

    @ManyToOne
    @JoinColumn(name = "tecnico_id")
    private Usuario tecnico;

    /** Puede ser null en una solicitud del cliente que todavía no se agenda. */
    private LocalDate fecha;

    @Column(length = 1000)
    private String descripcion;

    @Column(name = "sla_horas")
    private Integer slaHoras;

    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn;

    @Column(name = "ubicacion_autorizada", nullable = false)
    private boolean ubicacionAutorizada;

    @Column(name = "check_in")
    private LocalDateTime checkIn;

    @Column(name = "check_out")
    private LocalDateTime checkOut;

    @Column(name = "tiempo_real_min")
    private Integer tiempoRealMin;

    @Column(length = 2000)
    private String diagnostico;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "orden_actividades", joinColumns = @JoinColumn(name = "orden_id"))
    @OrderColumn(name = "posicion")
    @Column(name = "actividad", length = 300)
    private List<String> actividades = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "orden_materiales", joinColumns = @JoinColumn(name = "orden_id"))
    @OrderColumn(name = "posicion")
    @Column(name = "material", length = 300)
    private List<String> materiales = new ArrayList<>();

    @Column(length = 500)
    private String observacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Conformidad conformidad = Conformidad.SIN_REVISAR;

    @Column(name = "fecha_conformidad")
    private LocalDateTime fechaConformidad;

    @Column(name = "comentario_cliente", length = 1000)
    private String comentarioCliente;

    public OrdenTrabajo() {
    }

    public Long getId() { return id; }
    public Integer getNumero() { return numero; }
    public void setNumero(Integer numero) { this.numero = numero; }
    public Equipo getEquipo() { return equipo; }
    public void setEquipo(Equipo equipo) { this.equipo = equipo; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public Prioridad getPrioridad() { return prioridad; }
    public void setPrioridad(Prioridad prioridad) { this.prioridad = prioridad; }
    public EstadoOt getEstado() { return estado; }
    public void setEstado(EstadoOt estado) { this.estado = estado; }
    public Usuario getTecnico() { return tecnico; }
    public void setTecnico(Usuario tecnico) { this.tecnico = tecnico; }
    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public Integer getSlaHoras() { return slaHoras; }
    public void setSlaHoras(Integer slaHoras) { this.slaHoras = slaHoras; }
    public LocalDateTime getCreadaEn() { return creadaEn; }
    public void setCreadaEn(LocalDateTime creadaEn) { this.creadaEn = creadaEn; }
    public boolean isUbicacionAutorizada() { return ubicacionAutorizada; }
    public void setUbicacionAutorizada(boolean ubicacionAutorizada) { this.ubicacionAutorizada = ubicacionAutorizada; }
    public LocalDateTime getCheckIn() { return checkIn; }
    public void setCheckIn(LocalDateTime checkIn) { this.checkIn = checkIn; }
    public LocalDateTime getCheckOut() { return checkOut; }
    public void setCheckOut(LocalDateTime checkOut) { this.checkOut = checkOut; }
    public Integer getTiempoRealMin() { return tiempoRealMin; }
    public void setTiempoRealMin(Integer tiempoRealMin) { this.tiempoRealMin = tiempoRealMin; }
    public String getDiagnostico() { return diagnostico; }
    public void setDiagnostico(String diagnostico) { this.diagnostico = diagnostico; }
    public List<String> getActividades() { return actividades; }
    public List<String> getMateriales() { return materiales; }
    public String getObservacion() { return observacion; }
    public void setObservacion(String observacion) { this.observacion = observacion; }
    public Conformidad getConformidad() { return conformidad; }
    public void setConformidad(Conformidad conformidad) { this.conformidad = conformidad; }
    public LocalDateTime getFechaConformidad() { return fechaConformidad; }
    public void setFechaConformidad(LocalDateTime fechaConformidad) { this.fechaConformidad = fechaConformidad; }
    public String getComentarioCliente() { return comentarioCliente; }
    public void setComentarioCliente(String comentarioCliente) { this.comentarioCliente = comentarioCliente; }

    /** Texto visible, por ejemplo "OT-1041". */
    public String getCodigo() {
        return "OT-" + numero;
    }
}
