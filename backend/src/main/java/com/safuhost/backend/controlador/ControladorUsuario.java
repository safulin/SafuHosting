package com.safuhost.backend.controlador;

import com.safuhost.backend.modelo.Usuario;
import com.safuhost.backend.servicio.ServicioUsuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/usuarios")
@CrossOrigin(origins = "*")
public class ControladorUsuario {

    @Autowired
    private ServicioUsuario servicio;

    @PostMapping("/registro")
    public ResponseEntity<?> registrar(@RequestBody Usuario nuevo) {
        try {
            Usuario creado = servicio.registrar(nuevo);
            creado.setPassword(null);
            return ResponseEntity.ok(creado);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credenciales) {
        try {
            String username = credenciales.get("username");
            String password = credenciales.get("password");
            String token = servicio.login(username, password);
            return ResponseEntity.ok(Map.of("token", token));
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).body(e.getMessage());
        }
    }
}
