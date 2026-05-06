package com.safuhost.backend.repositorio;

import com.safuhost.backend.modelo.Servidor;
import com.safuhost.backend.modelo.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository

//Normalmente, si quisieras saber si un puerto está en la base de datos, tendrías que escribir una consulta SQL a mano, algo como:
//SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM servidor c WHERE c.puerto = 25565;
// pero vamos a usar Spring Data JPA, que tiene un sistema interno llamado "Query Methods" (Métodos de Consulta Derivados).
// Funciona como un traductor automático que lee el nombre que le pones a la función y genera el código SQL por ti.
// como no voy a usar postgres que es la base de datos que conozco prefiero usar un traductor como este, para hacerlo mas sencillo.

public interface RepositorioServidor extends JpaRepository<Servidor, Long> {
    // Busca si ya hay algún servidor guardado en SQLite que tenga este puerto asignado
    boolean existsByPuerto(Integer puerto);

    //Busca si ya exsiste el nombre que le mandan SELECT COUNT(*) FROM servidor WHERE nombre = (servidor.nombre)
    boolean existsByNombre(String nombre);

    // Devuelve solo los servidores cuyo propietario es el usuario indicado
    // Spring Data JPA traduce esto a: SELECT * FROM servidor WHERE propietario_id = ?
    List<Servidor> findByPropietario(Usuario propietario);
}