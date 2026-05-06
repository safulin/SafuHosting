package com.safuhost.backend.repositorio;

import com.safuhost.backend.modelo.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RepositorioUsuario extends JpaRepository<Usuario, Long> {

    // Spring Data JPA traduce este nombre a: SELECT * FROM usuario WHERE username = ? LIMIT 1
    // Optional porque puede que ese username no exista, en ese caso devolvera Optional vacio
    Optional<Usuario> findByUsername(String username);

    // Para validar que no se registre dos veces el mismo username
    boolean existsByUsername(String username);

    // Para validar que no se registre dos veces el mismo email
    boolean existsByEmail(String email);
}
