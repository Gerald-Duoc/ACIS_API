package com.example.acisapi.repositorio;

import com.example.acisapi.modelo.Equipo;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipoRepository extends JpaRepository<Equipo, Long> {

    List<Equipo> findByLocalIdOrderById(Long localId);
}
