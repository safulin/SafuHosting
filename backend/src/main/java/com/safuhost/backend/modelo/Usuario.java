package com.safuhost.backend.modelo;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

@Entity // Define que esta clase es una tabla en la base de datos
@Data   // Lombok genera getters, setters y constructores básicos
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // auto-incremental
    private Long id;

    private String username; // nombre de usuario único para hacer login
    private String email;    // correo electrónico
    private String password; // contraseña CIFRADA con BCrypt (NUNCA se guarda en texto plano)
}
