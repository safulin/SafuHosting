package com.safuhost.backend.repositorio;

import com.safuhost.backend.modelo.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

// Repositorio para la tabla usuario. Igual que con RepositorioServidor, usamos los Query Methods de Spring Data JPA:
// le pongo el nombre que quiero al metodo siguiendo su convencion (findBy..., existsBy...) y Spring Data JPA
// genera el SQL automaticamente. Sin escribir SQL a mano.

// Lo usa ServicioUsuario para registrar (existsBy...) y para hacer login (findByUsername).
// Tambien lo usa ServicioUsuario.getUsuarioActual() para sacar el usuario logueado del SecurityContext.

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
