package com.example.acisapi.repositorio;

import com.example.acisapi.modelo.Local;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocalRepository extends JpaRepository<Local, Long> {

    List<Local> findByClienteIdOrderById(Long clienteId);
}
