package com.example.acisapi.repositorio;

import com.example.acisapi.modelo.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    boolean existsByNombreIgnoreCase(String nombre);
}
