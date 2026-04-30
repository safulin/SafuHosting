package com.safuhost.backend.modelo;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

@Entity
@Data
public class Servidor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String version;
    private Integer puerto;
    private String estado; // Ej: "INICIANDO", "EN_LINEA", "APAGADO"
    private String idContenedor;
}