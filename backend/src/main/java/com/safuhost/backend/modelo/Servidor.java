package com.safuhost.backend.modelo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
public class Servidor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String version;
    private Integer puerto;
    private String estado;
    private String idContenedor;

    private String tipo;

    private String dificultad;
    private String modoJuego;
    private boolean pvp;

    private String urlIcono;
    private String administradores;
    private boolean usarWhitelist;
    private String listaBlanca;
    private boolean modoOnline;

    @Transient
    private List<String> modIniciales = new ArrayList<>();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "propietario_id")
    @JsonIgnoreProperties({"password"})
    private Usuario propietario;
}
