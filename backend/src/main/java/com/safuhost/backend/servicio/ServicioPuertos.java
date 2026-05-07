package com.safuhost.backend.servicio;

import com.safuhost.backend.repositorio.RepositorioServidor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.ServerSocket;

// Servicio dedicado a buscar puertos libres para asignar a los nuevos servidores.
// Lo llama ServicioServidor.crearServidor() cuando va a crear un contenedor de Docker
// y necesita saber en que puerto del PC va a escuchar Minecraft.

// La logica es: empezamos en el 25565 (puerto por defecto de Minecraft), y vamos subiendo de uno en uno
// hasta encontrar un puerto que no este usado ni en nuestra BD ni por ningun otro programa de Windows.

@Service
public class ServicioPuertos {

    // Conexión con nuestra base de datos (SQLite)
    @Autowired
    private RepositorioServidor repositorio;

    // Esta función busca el siguiente puerto libre empezando desde el 25565 que es el puerto que usa minecraft default,
    // igualmente hace una doble comprobacion con windows para asegurarnos que no exsistan programas de otras aplicaciones como en mi caso rustdesk
    // o asseto server y no cree colisiones, lo unico que no consigo encontrar la manera es de si alguna de estas no esta en uso, que windoes la detecte igual
    //pero como esta pensado para usarse en un windows server, o una maquina dedicada eso no deberia ser un problema

    public Integer encontrarPuertoLibre() {
        int puerto = 25565;

        while (true) {
            // 1. Preguntamos a SQLite si el puerto ya está asignado a otro servidor (aunque esté apagado)
            // estariamos haciendo algo asi: SELECT 1 FROM servidor WHERE puerto = 25565 LIMIT 1;
            // aqui Spring Data JPA, si nos diera minimo un 1 nos dara true, si da null o 0, dara false,
            boolean ocupadoEnBBDD = repositorio.existsByPuerto(puerto);

            // 2. Preguntamos a Windows si el puerto está siendo usado por otro programa en este momento
            boolean ocupadoEnSistema = !puertoDisponibleEnSistema(puerto);

            // Si el puerto NO está en nuestra DB Y tampoco está bloqueado por Windows, nos lo quedamos
            if (!ocupadoEnBBDD && !ocupadoEnSistema) {
                return puerto; // Rompe el bucle infinito y devuelve el puerto encontrado
            }

            // Si estaba ocupado, le sumamos 1 y el bucle vuelve a preguntar (ej. 25566, 25567...)
            puerto++;
        }
    }

    // Función "espía" que intenta abrir el puerto físicamente en Windows para ver si explota.
    // ServerSocket es una herramienta de java, se usa para crear programas que se queden escuchando, pero como lo que hace es tocar la puerta
    // del puerto, si lo intenta y windows lo deniega da fallo, asique nos sirve para que salte la excepcion.
    // El try-with-resources cierra el socket automaticamente al salir del bloque, asi no dejamos el puerto bloqueado por nosotros mismos.
    private boolean puertoDisponibleEnSistema(int puerto) {
        try (ServerSocket serverSocket = new ServerSocket(puerto)) {
            return true; // Éxito: Lo hemos podido abrir, significa que está libre
        } catch (IOException e) {
            return false; // Error: Ha saltado una excepción, alguien ya lo está usando
        }
    }
}
