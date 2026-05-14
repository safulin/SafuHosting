package com.safuhost.backend.servicio;

import com.safuhost.backend.modelo.Servidor;
import com.safuhost.backend.modelo.Usuario;
import com.safuhost.backend.repositorio.RepositorioUsuario;
import com.safuhost.backend.seguridad.UtilJwt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class ServicioUsuario {

    @Autowired
    private RepositorioUsuario repositorio;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UtilJwt utilJwt;

    public Usuario registrar(Usuario nuevo) {
        if (repositorio.existsByUsername(nuevo.getUsername())) {
            throw new RuntimeException("El nombre de usuario ya existe: " + nuevo.getUsername());
        }

        if (repositorio.existsByEmail(nuevo.getEmail())) {
            throw new RuntimeException("Ya existe una cuenta con ese email: " + nuevo.getEmail());
        }

        String passwordCifrada = passwordEncoder.encode(nuevo.getPassword());
        nuevo.setPassword(passwordCifrada);

        return repositorio.save(nuevo);
    }

    public String login(String username, String passwordPlana) {
        Usuario usuario = repositorio.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario o contraseña incorrectos"));

        if (!passwordEncoder.matches(passwordPlana, usuario.getPassword())) {
            throw new RuntimeException("Usuario o contraseña incorrectos");
        }

        return utilJwt.generarToken(username);
    }

    public Usuario getUsuarioActual() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return repositorio.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado: " + username));
    }

    public void verificarPropiedad(Servidor servidor) {
        Usuario actual = getUsuarioActual();
        if (servidor.getPropietario() == null
                || !servidor.getPropietario().getId().equals(actual.getId())) {
            throw new RuntimeException("No tienes permiso para acceder a este servidor");
        }
    }
}
