package com.safuhost.backend.repositorio;

import com.safuhost.backend.modelo.Servidor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RepositorioServidor extends JpaRepository<Servidor, Long> {

}