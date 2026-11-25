package com.example.acisapi.repositorio;

import com.example.acisapi.modelo.Sesion;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SesionRepository extends JpaRepository<Sesion, Long> {

    Optional<Sesion> findByToken(String token);

    long deleteByExpiraEnBefore(LocalDateTime momento);
}
