package com.safuhost.backend.repositorio;

import com.safuhost.backend.modelo.Servidor;
import com.safuhost.backend.modelo.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RepositorioServidor extends JpaRepository<Servidor, Long> {
    boolean existsByPuerto(Integer puerto);
    boolean existsByNombre(String nombre);
    List<Servidor> findByPropietario(Usuario propietario);
}
