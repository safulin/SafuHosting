package com.safuhost.backend.controlador;

import com.safuhost.backend.modelo.Usuario;
import com.safuhost.backend.servicio.ServicioUsuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

// Controlador con los dos endpoints publicos de la app: registro y login.
// Estos dos son los unicos que NO requieren JWT (lo declaramos en ConfiguracionSeguridad con permitAll()),
// porque obviamente para registrarse o loguearse aun no tienes token.
//
// El frontend los llama desde Registro.vue y Login.vue.
// Cuando login devuelve token correctamente, el frontend lo guarda en localStorage y a partir de ahi
// api.js lo mete automaticamente en cada peticion siguiente.

@RestController
@RequestMapping("/api/usuarios") // Todas las URLs de este controlador empiezan por aquí
@CrossOrigin(origins = "*")
public class ControladorUsuario {

    @Autowired
    private ServicioUsuario servicio;

    // Endpoint público para registrar nuevos usuarios.
    // Body esperado: { "username": "safu", "email": "safu@safu.com", "password": "1234" }
    // Devuelve el usuario creado (sin la contraseña) o un 400 si el username/email ya existian.
    @PostMapping("/registro")
    public ResponseEntity<?> registrar(@RequestBody Usuario nuevo) {
        try {
            Usuario creado = servicio.registrar(nuevo);
            // No devolvemos la contraseña aunque esté cifrada, por seguridad. La ponemos a null
            // antes de devolver el JSON, asi Jackson la serializa como null o la oculta segun config.
            creado.setPassword(null);
            return ResponseEntity.ok(creado);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Endpoint público para hacer login.
    // Body esperado: { "username": "safu", "password": "1234" }
    // Devuelve: { "token": "eyJhbGc..." }
    // El cliente debe guardar ese token y mandarlo en la cabecera Authorization de las siguientes peticiones.
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credenciales) {
        try {
            String username = credenciales.get("username");
            String password = credenciales.get("password");
            String token = servicio.login(username, password);
            // Devolvemos el token en formato JSON: { "token": "..." }
            // Lo envolvemos en un Map para tener un objeto JSON, no un string suelto
            return ResponseEntity.ok(Map.of("token", token));
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).body(e.getMessage()); // 401 = no autorizado
        }
    }
}
