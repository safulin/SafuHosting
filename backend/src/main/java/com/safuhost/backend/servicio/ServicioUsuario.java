package com.safuhost.backend.servicio;

import com.safuhost.backend.modelo.Servidor;
import com.safuhost.backend.modelo.Usuario;
import com.safuhost.backend.repositorio.RepositorioUsuario;
import com.safuhost.backend.seguridad.UtilJwt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

// Servicio que gestiona usuarios: registro, login y verificar quien es el dueño de cada servidor.
// Lo llaman:
//  - ControladorUsuario para registro y login (las rutas publicas)
//  - El resto de servicios (ServicioServidor, ServicioMods, ServicioWhitelist, ServicioDocker) para
//    saber quien es el usuario logueado y verificar que un servidor le pertenece antes de tocarlo.

@Service
public class ServicioUsuario {

    @Autowired
    private RepositorioUsuario repositorio;

    // Inyectamos el bean de PasswordEncoder (BCrypt) que creamos en ConfiguracionSeguridad
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

        // Ciframos la contraseña con BCrypt antes de guardarla.
        // El metodo encode() coge la contraseña en claro y devuelve el hash tipo "$2a$10$N9qo8uLOickgx2ZMRZoMye..."
        // Asi aunque alguien robe la BD, las contraseñas no son legibles ni reversibles.
        String passwordCifrada = passwordEncoder.encode(nuevo.getPassword());
        nuevo.setPassword(passwordCifrada);

        // Guardamos en SQLite y devolvemos el usuario ya con id asignado
        return repositorio.save(nuevo);
    }

    public String login(String username, String passwordPlana) {
        // Buscamos el usuario por su username
        Usuario usuario = repositorio.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario o contraseña incorrectos"));

        // Comparamos la contraseña en claro con el hash guardado en SQLite.
        // matches() cifra la contraseña en claro con el mismo algoritmo y compara los hashes,
        // NO descifra el hash original (es matematicamente imposible, BCrypt es de un solo sentido)
        if (!passwordEncoder.matches(passwordPlana, usuario.getPassword())) {
            // Mensaje genérico a propósito: no decimos si fue el usuario o la contraseña.
            // Asi un atacante no sabe si un usuario existe o no en la BD haciendo pruebas
            throw new RuntimeException("Usuario o contraseña incorrectos");
        }

        // Login correcto, generamos un token JWT que el cliente usará en las siguientes peticiones.
        // El cliente lo guarda en localStorage (lo hace api.js + Login.vue) y lo manda en cada peticion
        // en la cabecera Authorization: Bearer <token>
        return utilJwt.generarToken(username);
    }

    public Usuario getUsuarioActual() {
        // SecurityContextHolder es donde Spring guarda al usuario autenticado de la peticion actual.
        // El FiltroJwt mete ahí el username del token al validarlo (al inicio de cada peticion HTTP).
        // Esto SOLO funciona en hilos HTTP de Tomcat. En hilos de WebSocket no esta disponible,
        // por eso ConsolaWebSocketHandler usa el repositorio directamente sin pasar por este metodo.
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return repositorio.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario autenticado no encontrado: " + username));
    }

    public void verificarPropiedad(Servidor servidor) {
        // Cualquier acción sobre un servidor debe pasar por aquí para asegurar que el que la pide es su propietario.
        // Si alguien intenta acceder a un servidor que no es suyo (sabiendose el id), le denegamos la operación.
        // Lo llaman todos los servicios al inicio de cada metodo: obtenerPorId, instalarMod, eliminar, etc.
        Usuario actual = getUsuarioActual();
        if (servidor.getPropietario() == null
                || !servidor.getPropietario().getId().equals(actual.getId())) {
            throw new RuntimeException("No tienes permiso para acceder a este servidor");
        }
    }
}
