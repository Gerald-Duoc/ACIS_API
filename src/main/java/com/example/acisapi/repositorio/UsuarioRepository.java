package com.example.acisapi.repositorio;

import com.example.acisapi.modelo.Perfil;
import com.example.acisapi.modelo.Usuario;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByCorreoIgnoreCase(String correo);

    boolean existsByCorreoIgnoreCase(String correo);

    List<Usuario> findByPerfilOrderByNombre(Perfil perfil);
}
