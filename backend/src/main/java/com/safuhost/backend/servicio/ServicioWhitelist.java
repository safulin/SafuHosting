package com.safuhost.backend.servicio;

import com.safuhost.backend.modelo.Servidor;
import com.safuhost.backend.repositorio.RepositorioServidor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// Servicio que gestiona la lista blanca (whitelist) de jugadores permitidos por servidor.
// La lista se guarda en SQLite como un String separado por comas dentro del campo listaBlanca de Servidor:
// "Safu,Pepe,Juan". Lo dividimos al leer y lo unimos con join al guardar.

// Si el servidor esta EN_LINEA tambien mandamos el comando "whitelist add/remove" directamente a la consola
// de Minecraft via Docker, asi no hace falta reiniciar el servidor para que el cambio surta efecto.

// Lo llama ServicioServidor (que delega los metodos del controlador a este servicio).

@Service
public class ServicioWhitelist {

    // Conexión con nuestra base de datos (SQLite)
    @Autowired
    private RepositorioServidor repositorio;

    // Servicio que se encarga de hablar con Docker para enviar comandos al contenedor
    @Autowired
    private ServicioDocker servicioDocker;

    // Servicio de usuarios para verificar que el servidor pertenece al usuario logueado
    @Autowired
    private ServicioUsuario servicioUsuario;

    public List<String> obtenerWhitelist(Long id) {
        Servidor servidor = repositorio.findById(id)
                .orElseThrow(() -> new RuntimeException("No existe ningún servidor con el id: " + id));
        servicioUsuario.verificarPropiedad(servidor);

        // Si la lista está vacía o es null devolvemos una lista vacía
        if (servidor.getListaBlanca() == null || servidor.getListaBlanca().isBlank()) {
            return new ArrayList<>();
        }

        // La listaBlanca se guarda como "Jugador1,Jugador2,Jugador3"
        // La dividimos por comas y devolvemos cada nombre como elemento de la lista
        return Arrays.asList(servidor.getListaBlanca().split(","));
    }

    public Servidor añadirAWhitelist(Long id, String jugador) {
        Servidor servidor = repositorio.findById(id)
                .orElseThrow(() -> new RuntimeException("No existe ningún servidor con el id: " + id));
        servicioUsuario.verificarPropiedad(servidor);

        // Construimos la nueva lista añadiendo el jugador
        String listaActual = servidor.getListaBlanca();
        if (listaActual == null || listaActual.isBlank()) {
            // Si la lista estaba vacía el jugador es el primero
            servidor.setListaBlanca(jugador);
        } else if (!listaActual.contains(jugador)) {
            // Solo añadimos si el jugador no estaba ya en la lista
            servidor.setListaBlanca(listaActual + "," + jugador);
        }

        // Si el servidor está en línea le mandamos el comando directamente a Docker
        // así no hace falta reiniciarlo para que surta efecto.
        // Si esta apagado no pasa nada, al iniciarlo otra vez itzg lee el campo WHITELIST de las env vars y lo aplica.
        if ("EN_LINEA".equals(servidor.getEstado())) {
            servicioDocker.ejecutarComandoEnContenedor(servidor.getIdContenedor(), "whitelist add " + jugador);
        }

        return repositorio.save(servidor);
    }

    public Servidor quitarDeWhitelist(Long id, String jugador) {
        Servidor servidor = repositorio.findById(id)
                .orElseThrow(() -> new RuntimeException("No existe ningún servidor con el id: " + id));
        servicioUsuario.verificarPropiedad(servidor);

        // Filtramos la lista quitando el jugador que queremos eliminar.
        // Convertimos a ArrayList porque Arrays.asList devuelve una lista de tamaño fijo (no se puede usar .remove)
        if (servidor.getListaBlanca() != null) {
            List<String> lista = new ArrayList<>(Arrays.asList(servidor.getListaBlanca().split(",")));
            lista.remove(jugador);
            servidor.setListaBlanca(String.join(",", lista));
        }

        // Si el servidor está en línea le mandamos el comando directamente a Docker (igual que en añadir)
        if ("EN_LINEA".equals(servidor.getEstado())) {
            servicioDocker.ejecutarComandoEnContenedor(servidor.getIdContenedor(), "whitelist remove " + jugador);
        }

        return repositorio.save(servidor);
    }
}
