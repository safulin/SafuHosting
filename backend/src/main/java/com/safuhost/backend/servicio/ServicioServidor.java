package com.safuhost.backend.servicio;

import com.safuhost.backend.repositorio.RepositorioServidor;
import com.github.dockerjava.api.DockerClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.ServerSocket;


import com.safuhost.backend.modelo.Servidor;
import com.github.dockerjava.api.command.CreateContainerResponse; // este import es el que hace que docker nos devuelva el id del contenedor de docker
import com.github.dockerjava.api.model.ExposedPort; // Define la "puerta interna" del contenedor; en Minecraft siempre es la 25565
import com.github.dockerjava.api.model.HostConfig;  // Configuración del "exterior": define cómo el contenedor se relaciona con tu
// PC (puertos y carpetas físicas)
import com.github.dockerjava.api.model.Ports; // El gestor de "túneles" que mapea tus puertos de Windows con los puertos internos de Docker
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;


@Service
public class ServicioServidor {

    // Conexión con nuestra base de datos (SQLite)
    @Autowired
    private RepositorioServidor repositorio;

    // con el autowired tambien llamamos al dockerclient que esta creado en el configuracion/configuraciondocker, usa una libreria para hablar con docker
    // desde spring, es como un mando a distancia
    @Autowired
    private DockerClient dockerClient;

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

    // Función "espía" que intenta abrir el puerto físicamente en Windows para ver si explota
    // serversocket es una herramienta de java, se usa para crear programas que se queden escuchando, pero como lo que hace es tocar la puerta
    // del puerto, si lo intenta y windows lo deniega da fallo, asique nos sirve para que salte la excepcion.
    private boolean puertoDisponibleEnSistema(int puerto) {
        try (ServerSocket serverSocket = new ServerSocket(puerto)) {
            return true; // Éxito: Lo hemos podido abrir, significa que está libre
        } catch (IOException e) {
            return false; // Error: Ha saltado una excepción, alguien ya lo está usando
        }
    }



    public Servidor crearServidor(Servidor nuevo) {

        //este if mira si el nombre que le quieren poner es un duplicado si lo es se detiene el codigo.
        if (repositorio.existsByNombre(nuevo.getNombre())) {
            // Si el nombre ya existe, lanzamos una excepción y el código se detiene aqui
            throw new RuntimeException("¡Error! Ya existe un servidor con el nombre: " + nuevo.getNombre());
        }
        // 1. Buscamos un puerto disponible en el sistema y en la DB
        Integer puertoLibre = encontrarPuertoLibre();
        nuevo.setPuerto(puertoLibre);

        // 2. Definimos la carpeta donde se guardará el mundo (Ej: C:/mc-servers/MiMundo)

        String rutaLocal = "C:/mc-servers/" + nuevo.getNombre();

        // 3. Preparamos la configuración para Docker (Variables de Entorno)
        List<String> env = new ArrayList<>();
        env.add("EULA=TRUE");
        env.add("VERSION=" + nuevo.getVersion());
        env.add("TYPE=" + nuevo.getTipo());
        env.add("DIFFICULTY=" + nuevo.getDificultad());
        env.add("MODE=" + nuevo.getModoJuego());
        env.add("PVP=" + nuevo.isPvp());
        env.add("ONLINE_MODE=" + nuevo.isModoOnline());
        // Gestión de Whitelist: Interruptor (boolean) y lista de nombres
        // aqui le estamos diciendo que si usarwhitelist es true, si la lista de whitelist no es null o esta vacia, añada la lista a la variable de whitelist
        env.add("ENABLE_WHITELIST=" + nuevo.isUsarWhitelist());
        if (nuevo.isUsarWhitelist() && nuevo.getListaBlanca() != null && !nuevo.getListaBlanca().isEmpty()) {
            env.add("WHITELIST=" + nuevo.getListaBlanca());
        }
        // Administradores (OPS) y Autopause forzado de 5 min (300s)
        env.add("OPS=" + nuevo.getAdministradores());
        env.add("AUTOPAUSE=true");
        env.add("AUTOPAUSE_TIMEOUT_EST=300");

        // 4. Mapeo de puertos: Red del PC (puertoLibre) -> Red del Contenedor (25565)
        // esto es complejo entonces voy a dejar todo comentado
        ExposedPort puertoInterno = ExposedPort.tcp(25565); // esto seria como que estamos señalando a la puerta especifica de docker
        //siempre sera 25565 porque es el de minecraft, entonces via tcp siempre mirariamos al puerto 25565 de el contenedor de docker


        Ports portBindings = new Ports(); // aqui estamos creando una especia de mapa donde le diremos que cables del exterior(pc) van con
        // los del interior(docker) pero aun no hemos metido nada


        portBindings.bind(puertoInterno, Ports.Binding.bindPort(puertoLibre)); // aqui es donde usamos el metodo para encontrar el puerto libre,
        //lo usamos para bindearlo(unirlo) con el portbinding(es un metodo) a el puertointerno(docker) que tenemos harcoded.


        // Esto fuerza a Docker a descargar la imagen si no la tiene
        try {
            // Esto fuerza la descarga y espera a que termine
            dockerClient.pullImageCmd("itzg/minecraft-server")
                    .withTag("latest")
                    .start()
                    .awaitCompletion();
        } catch (InterruptedException e) {
            // Si algo interrumpe la descarga, lanzamos un error
            Thread.currentThread().interrupt();
            throw new RuntimeException("La descarga de la imagen de Minecraft fue interrumpida", e);
        }
        // 5. Creación del contenedor con volumen persistente en el disco duro
        //aqui creamos la variable donde docker nos devolvera el id, tambien le decimos a docker, crea un contenedor usando esta imagen(plantilla):itzg/minecraft-server
        // y luego le vamos dando variables que docker almacenara como metadatos
        // que la imagen(itzg) entendera y usara para crear el servidor
        CreateContainerResponse container = dockerClient.createContainerCmd("itzg/minecraft-server")
                .withName("mc-" + nuevo.getNombre())
                .withEnv(env)
                .withHostConfig(HostConfig.newHostConfig()
                        .withPortBindings(portBindings)
                        .withBinds(new com.github.dockerjava.api.model.Bind(rutaLocal, new com.github.dockerjava.api.model.Volume("/data"))))
                .exec();
// CONEXIÓN FÍSICA: Vinculamos la carpeta de Windows (rutaLocal) con la carpeta interna del server (/data)
// Es vital: sin esto, al borrar el contenedor de Docker se perdería el mundo, los inventarios y los progresos.
        // 6. Ordenamos a Docker que encienda el servidor inmediatamente
        dockerClient.startContainerCmd(container.getId()).exec();

        // 7. Registramos el éxito en SQLite para no olvidar este servidor
        nuevo.setIdContenedor(container.getId());
        nuevo.setEstado("INICIANDO");
        return repositorio.save(nuevo);
    }

    public List<Servidor> listarTodos() {
        return repositorio.findAll(); // el findall es un un metodo de jparepository, que hace un select * from servidor que es la tabla que alverga todos
        //los datos
    }


    // findById() viene gratis con JpaRepository, igual que findAll(). Internamente hace: SELECT * FROM servidor WHERE id = ?.
    // Pero ojo, no devuelve un Servidor directamente, devuelve un Optional<Servidor>
    public Servidor obtenerPorId(Long id) {
        // si está vacío (no existe ese id) lanzamos una excepción con un mensaje claro
        return repositorio.findById(id)
                .orElseThrow(() -> new RuntimeException("No existe ningún servidor con el id: " + id));
    }

    public Servidor pararServidor(Long id) {
        // 1. Buscamos el servidor en SQLite para obtener su idContenedor
        Servidor servidor = obtenerPorId(id); //cuando hacemos un obtenePorId al fianal buscamos con el findbyid y lo convertimos en un objeto con todos los
        //campos rellenos

        // 2. Le decimos a Docker que pare el contenedor
        dockerClient.stopContainerCmd(servidor.getIdContenedor()).exec();

        // 3. Actualizamos el estado en SQLite para que la app sepa que está apagado
        servidor.setEstado("APAGADO");
        return repositorio.save(servidor);
    }

    public Servidor iniciarServidor(Long id) {
        // 1. Buscamos el servidor en SQLite para obtener su idContenedor
        Servidor servidor = obtenerPorId(id);

        // 2. Le decimos a Docker que arranque el contenedor (el mismo que se creó al principio,
        // con toda su configuración ya guardada, no hace falta volver a configurar nada)
        dockerClient.startContainerCmd(servidor.getIdContenedor()).exec();

        // 3. Actualizamos el estado en SQLite
        servidor.setEstado("INICIANDO");
        return repositorio.save(servidor);
    }

}