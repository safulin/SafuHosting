package com.safuhost.backend.controlador;

import com.safuhost.backend.modelo.Servidor;
import com.safuhost.backend.servicio.ServicioServidor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController // Indica que esta clase es una "puerta" para recibir peticiones web (API)
@RequestMapping("/api/servidores") // Todas las URLs de este controlador empezarán por aquí
@CrossOrigin(origins = "*") // esto hace que el front pueda leer al backend sino hay firewall
public class ControladorServidor {

    @Autowired
    private ServicioServidor servicio; // Llamamos a nuestro a la clase que tiene los metodos para crear el servidor

    // aqui creamos el endpoint, para crear un servidor hay que ir a /crear
    @PostMapping("/crear")
    public ResponseEntity<?> crearServidor(@RequestBody Servidor nuevo) {
        try {
            // Intentamos ejecutar los metodos del servicioServidor
            Servidor creado = servicio.crearServidor(nuevo);

            // Si sale bien, devolvemos el servidor con su ID de Docker y puerto (Status 200)
            return ResponseEntity.ok(creado);
        } catch (RuntimeException e) {
            // Si el nombre está repetido, el servicio lanza una excepción
            // y  devolve un error (Status 400) con el mensaje de "Nombre ya existe"
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/todos") //peticion get, no importamos nada, solo devolvemos el listado que es una array que devolveremos en json.
    public List<Servidor> listarTodos() {
        return servicio.listarTodos();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerPorId(@PathVariable Long id) {
        try {
            Servidor servidor = servicio.obtenerPorId(id);
            return ResponseEntity.ok(servidor);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/parar")
    public ResponseEntity<?> pararServidor(@PathVariable Long id) {
        try {
            Servidor servidor = servicio.pararServidor(id);
            return ResponseEntity.ok(servidor);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{id}/iniciar")
    public ResponseEntity<?> iniciarServidor(@PathVariable Long id) {
        try {
            Servidor servidor = servicio.iniciarServidor(id);
            return ResponseEntity.ok(servidor);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}