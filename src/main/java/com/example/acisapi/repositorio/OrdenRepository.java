package com.example.acisapi.repositorio;

import com.example.acisapi.modelo.EstadoOt;
import com.example.acisapi.modelo.OrdenTrabajo;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrdenRepository extends JpaRepository<OrdenTrabajo, Long> {

    /** Las más nuevas primero. */
    List<OrdenTrabajo> findAllByOrderByIdDesc();

    /** Último número de OT usado (1040 si todavía no hay ninguna, así la primera es OT-1041). */
    @Query("select coalesce(max(o.numero), 1040) from OrdenTrabajo o")
    Integer maxNumero();

    /** HU-35: intervenciones anteriores de un equipo. */
    List<OrdenTrabajo> findByEquipoIdAndEstadoOrderByFechaDesc(Long equipoId, EstadoOt estado);

    /** Cuántas OT sin cerrar tiene un técnico (para mostrar su carga al asignar). */
    long countByTecnicoIdAndEstadoNot(Long tecnicoId, EstadoOt estado);
}
