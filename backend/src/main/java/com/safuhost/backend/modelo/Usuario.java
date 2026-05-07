package com.safuhost.backend.modelo;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

// Esta clase representa la tabla "usuario" en la base de datos SQLite.
// Cada usuario tiene su id, su username, su email y su contraseña cifrada.
// Spring Data JPA + Hibernate se encargan de crear la tabla y mapear los campos automaticamente
// gracias a las anotaciones @Entity y @Id.

// Lo mapea ServidorRepositorio... no espera, este lo usa RepositorioUsuario para hacer las consultas,
// y ServicioUsuario para registrar y hacer login.

@Entity // Define que esta clase es una tabla en la base de datos
@Data   // Lombok genera getters, setters y constructores básicos a tiempo de compilacion, asi no tengo que escribirlos a mano
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // auto-incremental, SQLite asigna el id solo al insertar
    private Long id;

    private String username; // nombre de usuario único para hacer login. Se valida en ServicioUsuario.registrar() que no exista ya
    private String email;    // correo electrónico, tambien unico
    private String password; // contraseña CIFRADA con BCrypt (NUNCA se guarda en texto plano).
                             // El cifrado lo hace ServicioUsuario.registrar() antes de llamar a save().
}
