package com.safuhost.backend.controlador;

import com.safuhost.backend.modelo.Usuario;
import com.safuhost.backend.servicio.ServicioUsuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/usuarios") // Todas las URLs de este controlador empiezan por aquí
@CrossOrigin(origins = "*")
public class ControladorUsuario {

    @Autowired
    private ServicioUsuario servicio;

    // Endpoint público para registrar nuevos usuarios
    // Body esperado: { "username": "safu", "email": "safu@safu.com", "password": "1234" }
    @PostMapping("/registro")
    public ResponseEntity<?> registrar(@RequestBody Usuario nuevo) {
        try {
            Usuario creado = servicio.registrar(nuevo);
            // No devolvemos la contraseña aunque esté cifrada, por seguridad
            creado.setPassword(null);
            return ResponseEntity.ok(creado);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Endpoint público para hacer login
    // Body esperado: { "username": "safu", "password": "1234" }
    // Devuelve: { "token": "eyJhbGc..." }
    // El cliente debe guardar ese token y mandarlo en la cabecera Authorization de las siguientes peticiones
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credenciales) {
        try {
            String username = credenciales.get("username");
            String password = credenciales.get("password");
            String token = servicio.login(username, password);
            // Devolvemos el token en formato JSON: { "token": "..." }
            return ResponseEntity.ok(Map.of("token", token));
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).body(e.getMessage()); // 401 = no autorizado
        }
    }
}
