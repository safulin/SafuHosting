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

    // Inyectamos el bean de BCrypt que creamos en ConfiguracionSeguridad
    @Autowired
    private PasswordEncoder passwordEncoder;

    // Utilidad para generar tokens JWT al hacer login
    @Autowired
    private UtilJwt utilJwt;

    public Usuario registrar(Usuario nuevo) {
        // Validamos que el username no esté ya cogido
        if (repositorio.existsByUsername(nuevo.getUsername())) {
            throw new RuntimeException("El nombre de usuario ya existe: " + nuevo.getUsername());
        }

        // Validamos que el email no esté ya registrado
        if (repositorio.existsByEmail(nuevo.getEmail())) {
            throw new RuntimeException("Ya existe una cuenta con ese email: " + nuevo.getEmail());
        }

        // Ciframos la contraseña con BCrypt antes de guardarla
        // El metodo encode() coge la contraseña en claro y devuelve el hash
        String passwordCifrada = passwordEncoder.encode(nuevo.getPassword());
        nuevo.setPassword(passwordCifrada);

        // Guardamos en SQLite y devolvemos el usuario ya con id asignado
        return repositorio.save(nuevo);
    }

    public String login(String username, String passwordPlana) {
        // Buscamos el usuario por su username
        Usuario usuario = repositorio.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario o contraseña incorrectos"));

        // Comparamos la contraseña en claro con el hash guardado en SQLite
        // matches() cifra la contraseña en claro y compara los hashes, NO descifra el hash original (es imposible)
        if (!passwordEncoder.matches(passwordPlana, usuario.getPassword())) {
            // Mensaje genérico a propósito: no decimos si fue el usuario o la contraseña
            // así un atacante no sabe si un usuario existe o no
            throw new RuntimeException("Usuario o contraseña incorrectos");
        }

        // Login correcto, generamos un token JWT que el cliente usará en las siguientes peticiones
        return utilJwt.generarToken(username);
    }

    public Usuario getUsuarioActual() {
        // SecurityContextHolder es donde Spring guarda al usuario autenticado de la peticion actual
        // El FiltroJwt mete ahí el username del token al validarlo
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return repositorio.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado: " + username));
    }

    public void verificarPropiedad(Servidor servidor) {
        // Cualquier acción sobre un servidor debe pasar por aquí para asegurar que el que la pide es su propietario
        // Si alguien intenta acceder a un servidor que no es suyo, le denegamos la operación
        Usuario actual = getUsuarioActual();
        if (servidor.getPropietario() == null
                || !servidor.getPropietario().getId().equals(actual.getId())) {
            throw new RuntimeException("No tienes permiso para acceder a este servidor");
        }
    }
}
