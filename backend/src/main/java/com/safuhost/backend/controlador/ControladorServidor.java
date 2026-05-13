package com.safuhost.backend.controlador;

import com.safuhost.backend.modelo.Servidor;
import com.safuhost.backend.servicio.ServicioServidor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Arrays;

@RestController
@RequestMapping("/api/servidores")
@CrossOrigin(origins = "*")
public class ControladorServidor {

    @Autowired
    private ServicioServidor servicio;

    @PostMapping("/crear")
    public ResponseEntity<?> crearServidor(@RequestBody Servidor nuevo) {
        try {
            Servidor creado = servicio.crearServidor(nuevo);
            return ResponseEntity.ok(creado);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/todos")
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

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminarServidor(@PathVariable Long id) {
        try {
            servicio.eliminarServidor(id);
            return ResponseEntity.ok("Servidor eliminado correctamente");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizarServidor(@PathVariable Long id, @RequestBody Servidor datos) {
        try {
            Servidor servidor = servicio.actualizarServidor(id, datos);
            return ResponseEntity.ok(servidor);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{id}/estado")
    public ResponseEntity<?> obtenerEstado(@PathVariable Long id) {
        try {
            String estado = servicio.obtenerEstado(id);
            return ResponseEntity.ok(estado);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{id}/mods")
    public ResponseEntity<?> listarMods(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(servicio.listarMods(id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/mods/buscar")
    public ResponseEntity<?> buscarMods(@RequestParam String query) {
        try {
            return ResponseEntity.ok(servicio.buscarModsModrinth(query));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/mods/verificar")
    public ResponseEntity<?> verificarMod(
            @RequestParam String modId,
            @RequestParam String tipo,
            @RequestParam String version) {
        try {
            return ResponseEntity.ok(servicio.verificarCompatibilidadMod(modId, tipo, version));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{id}/mods/instalar/{modrinthId}")
    public ResponseEntity<?> instalarMod(@PathVariable Long id, @PathVariable String modrinthId) {
        try {
            String nombre = servicio.instalarModModrinth(id, modrinthId);
            return ResponseEntity.ok("Mod instalado: " + nombre);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}/mods/{nombreMod}")
    public ResponseEntity<?> eliminarMod(@PathVariable Long id, @PathVariable String nombreMod) {
        try {
            servicio.eliminarMod(id, nombreMod);
            return ResponseEntity.ok("Mod eliminado: " + nombreMod);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{id}/consola/comando")
    public ResponseEntity<?> enviarComando(@PathVariable Long id, @RequestBody String comando) {
        try {
            servicio.enviarComandoConsola(id, comando);
            return ResponseEntity.ok("Comando enviado: " + comando);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{id}/whitelist")
    public ResponseEntity<?> obtenerWhitelist(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(servicio.obtenerWhitelist(id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{id}/whitelist/{jugador}")
    public ResponseEntity<?> añadirAWhitelist(@PathVariable Long id, @PathVariable String jugador) {
        try {
            return ResponseEntity.ok(servicio.añadirAWhitelist(id, jugador));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}/whitelist/{jugador}")
    public ResponseEntity<?> quitarDeWhitelist(@PathVariable Long id, @PathVariable String jugador) {
        try {
            return ResponseEntity.ok(servicio.quitarDeWhitelist(id, jugador));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/versiones")
    public List<String> obtenerVersiones() {
        return Arrays.asList(
                "LATEST",
                "1.21.4", "1.21.3", "1.21.2", "1.21.1", "1.21",
                "1.20.6", "1.20.5", "1.20.4", "1.20.3", "1.20.2", "1.20.1", "1.20",
                "1.19.4", "1.19.3", "1.19.2", "1.19.1", "1.19",
                "1.18.2", "1.18.1", "1.18",
                "1.17.1", "1.17",
                "1.16.5", "1.16.4", "1.16.3", "1.16.2", "1.16.1", "1.16",
                "1.15.2", "1.15.1", "1.15",
                "1.14.4", "1.14.3", "1.14.2", "1.14.1", "1.14",
                "1.13.2", "1.13.1", "1.13",
                "1.12.2", "1.12.1", "1.12",
                "1.11.2", "1.11.1", "1.11",
                "1.10.2", "1.10",
                "1.9.4", "1.9.2", "1.9",
                "1.8.9", "1.8.8", "1.8.7", "1.8.6", "1.8.5", "1.8.4", "1.8.3", "1.8"
        );
    }
}
