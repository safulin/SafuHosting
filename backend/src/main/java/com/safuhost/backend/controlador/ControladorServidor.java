package com.safuhost.backend.controlador;

import com.safuhost.backend.modelo.Servidor;
import com.safuhost.backend.servicio.ServicioServidor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Arrays;

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
    // @PathVariable coge el id de la URL (/api/servidores/1 → id = 1)
    // @RequestBody coge el JSON del cuerpo de la petición y lo convierte en un objeto Servidor
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

    // Busca mods en Modrinth (la API pública de mods de Minecraft)
    // Ejemplo: GET /api/servidores/mods/buscar?query=jei
    @GetMapping("/mods/buscar")
    public ResponseEntity<?> buscarMods(@RequestParam String query) {
        try {
            return ResponseEntity.ok(servicio.buscarModsModrinth(query));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Instala un mod descargándolo desde Modrinth
    // El modrinthId es el id que devuelve la búsqueda (campo project_id)
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

    // Recibe un comando como String plano en el body y lo ejecuta en la consola del servidor
    // Ejemplo body: "say Hola a todos"  o  "op Safu"
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

    // Devuelve la lista de versiones de Minecraft disponibles para crear un servidor
    // La imagen itzg/minecraft-server soporta estas versiones de forma nativa
    // El frontend usará este endpoint para mostrar el desplegable al crear un servidor
    @GetMapping("/versiones")
    public List<String> obtenerVersiones() {
        return Arrays.asList(
                "LATEST",
                // 1.21
                "1.21.4", "1.21.3", "1.21.2", "1.21.1", "1.21",
                // 1.20
                "1.20.6", "1.20.5", "1.20.4", "1.20.3", "1.20.2", "1.20.1", "1.20",
                // 1.19
                "1.19.4", "1.19.3", "1.19.2", "1.19.1", "1.19",
                // 1.18
                "1.18.2", "1.18.1", "1.18",
                // 1.17
                "1.17.1", "1.17",
                // 1.16
                "1.16.5", "1.16.4", "1.16.3", "1.16.2", "1.16.1", "1.16",
                // 1.15
                "1.15.2", "1.15.1", "1.15",
                // 1.14
                "1.14.4", "1.14.3", "1.14.2", "1.14.1", "1.14",
                // 1.13
                "1.13.2", "1.13.1", "1.13",
                // 1.12
                "1.12.2", "1.12.1", "1.12",
                // 1.11
                "1.11.2", "1.11.1", "1.11",
                // 1.10
                "1.10.2", "1.10",
                // 1.9
                "1.9.4", "1.9.2", "1.9",
                // 1.8
                "1.8.9", "1.8.8", "1.8.7", "1.8.6", "1.8.5", "1.8.4", "1.8.3", "1.8"
        );
    }
}