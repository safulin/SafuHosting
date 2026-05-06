package com.safuhost.backend.servicio;

import com.safuhost.backend.repositorio.RepositorioServidor;
import com.github.dockerjava.api.DockerClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


import com.safuhost.backend.modelo.Servidor;
import com.github.dockerjava.api.command.CreateContainerResponse; // este import es el que hace que docker nos devuelva el id del contenedor de docker
import com.github.dockerjava.api.model.ExposedPort; // Define la "puerta interna" del contenedor; en Minecraft siempre es la 25565
import com.github.dockerjava.api.model.HostConfig;  // Configuración del "exterior": define cómo el contenedor se relaciona con tu
// PC (puertos y carpetas físicas)
import com.github.dockerjava.api.model.Ports; // El gestor de "túneles" que mapea tus puertos de Windows con los puertos internos de Docker
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

    @Autowired
    private ServicioPuertos servicioPuertos;

    @Autowired
    private ServicioDocker servicioDocker;

    @Autowired
    private ServicioMods servicioMods;

    // Servicio que gestiona la whitelist (lista blanca de jugadores)
    @Autowired
    private ServicioWhitelist servicioWhitelist;

    // Servicio de usuarios para saber quien es el usuario logueado y verificar propiedad
    @Autowired
    private ServicioUsuario servicioUsuario;

    public Servidor crearServidor(Servidor nuevo) {

        //este if mira si el nombre que le quieren poner es un duplicado si lo es se detiene el codigo.
        if (repositorio.existsByNombre(nuevo.getNombre())) {
            // Si el nombre ya existe, lanzamos una excepción y el código se detiene aqui
            throw new RuntimeException("¡Error! Ya existe un servidor con el nombre: " + nuevo.getNombre());
        }

        // Asociamos el servidor al usuario que está logueado en este momento
        // Lo sacamos del SecurityContextHolder de Spring (lo metió ahí FiltroJwt)
        nuevo.setPropietario(servicioUsuario.getUsuarioActual());

        // 1. Buscamos un puerto disponible en el sistema y en la DB
        Integer puertoLibre = servicioPuertos.encontrarPuertoLibre();
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
        env.add("AUTOPAUSE=false"); // desactivado mientras desarrollamos para que los comandos funcionen sin necesidad de jugadores conectados
        env.add("AUTOPAUSE_TIMEOUT_EST=300");
        env.add("CREATE_CONSOLE_IN_PIPE=true"); // necesario para poder enviar comandos con mc-send-to-console

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
//  sin esto, al borrar el contenedor de Docker se perdería el mundo, los inventarios y los progresos.
        // 6. Ordenamos a Docker que encienda el servidor inmediatamente
        dockerClient.startContainerCmd(container.getId()).exec();

        // 7. Registramos el éxito en SQLite para no olvidar este servidor
        nuevo.setIdContenedor(container.getId());
        nuevo.setEstado("INICIANDO");
        return repositorio.save(nuevo);
    }

    public List<Servidor> listarTodos() {
        // Antes devolvíamos todos los servidores. Ahora SOLO los del usuario actual.
        // Cada usuario solo puede ver sus propios servidores.
        return repositorio.findByPropietario(servicioUsuario.getUsuarioActual());
    }


    // findById() viene gratis con JpaRepository, igual que findAll(). Internamente hace: SELECT * FROM servidor WHERE id = ?.
    // Pero ojo, no devuelve un Servidor directamente, devuelve un Optional<Servidor>
    public Servidor obtenerPorId(Long id) {
        // si está vacío (no existe ese id) lanzamos una excepción con un mensaje claro
        Servidor servidor = repositorio.findById(id)
                .orElseThrow(() -> new RuntimeException("No existe ningún servidor con el id: " + id));

        // Verificamos que el usuario logueado sea el propietario antes de devolver el servidor
        // Si no lo es, lanza excepción y devuelve 400
        servicioUsuario.verificarPropiedad(servidor);
        return servidor;
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

    public void eliminarServidor(Long id) {
        // 1. Buscamos el servidor en SQLite para obtener su idContenedor
        Servidor servidor = obtenerPorId(id);

        // 2. Intentamos parar y eliminar el contenedor de Docker
        // Usamos try-catch independientes porque puede que el contenedor ya no exista en Docker
        // (por ejemplo si el usuario lo borró manualmente desde Docker Desktop)
        // En ese caso no queremos que falle, queremos seguir adelante y borrarlo de SQLite igualmente
        try {
            dockerClient.stopContainerCmd(servidor.getIdContenedor()).exec();
        } catch (RuntimeException e) {
            // El contenedor ya estaba parado o no existe en Docker, ignoramos el error y continuamos
        }

        try {
            dockerClient.removeContainerCmd(servidor.getIdContenedor()).exec();
        } catch (RuntimeException e) {
            // El contenedor ya no existe en Docker, ignoramos el error y continuamos
        }

        // 3. Borramos el registro de SQLite pase lo que pase con Docker
        repositorio.deleteById(id);
    }

    public Servidor actualizarServidor(Long id, Servidor datos) {
        // 1. Buscamos el servidor existente en SQLite
        Servidor servidor = obtenerPorId(id);

        // 2. Sobreescribimos solo los campos que se pueden cambiar en caliente
        // Puerto, idContenedor y version NO se tocan porque están ligados al contenedor de Docker
        servidor.setNombre(datos.getNombre());
        servidor.setDificultad(datos.getDificultad());
        servidor.setModoJuego(datos.getModoJuego());
        servidor.setPvp(datos.isPvp());
        servidor.setUsarWhitelist(datos.isUsarWhitelist());
        servidor.setListaBlanca(datos.getListaBlanca());
        servidor.setAdministradores(datos.getAdministradores());
        servidor.setModoOnline(datos.isModoOnline());
        servidor.setUrlIcono(datos.getUrlIcono());

        // 3. Guardamos los cambios en SQLite y devolvemos el servidor actualizado
        return repositorio.save(servidor);
    }

    // a partir de aqui ServicioServidor solo hace de coordinador.
    // asi el ControladorServidor sigue hablando solo con ServicioServidor sin necesidad de cambiar nada.

    public String obtenerEstado(Long id) {
        return servicioDocker.obtenerEstado(id);
    }

    public void enviarComandoConsola(Long id, String comando) {
        Servidor servidor = obtenerPorId(id);
        servicioDocker.ejecutarComandoEnContenedor(servidor.getIdContenedor(), comando);
    }

    public List<String> listarMods(Long id) {
        return servicioMods.listarMods(id);
    }

    public Object buscarModsModrinth(String query) {
        return servicioMods.buscarModsModrinth(query);
    }

    public String instalarModModrinth(Long id, String modrinthId) throws java.io.IOException {
        return servicioMods.instalarModModrinth(id, modrinthId);
    }

    public void eliminarMod(Long id, String nombreMod) {
        servicioMods.eliminarMod(id, nombreMod);
    }

    public List<String> obtenerWhitelist(Long id) {
        return servicioWhitelist.obtenerWhitelist(id);
    }

    public Servidor añadirAWhitelist(Long id, String jugador) {
        return servicioWhitelist.añadirAWhitelist(id, jugador);
    }

    public Servidor quitarDeWhitelist(Long id, String jugador) {
        return servicioWhitelist.quitarDeWhitelist(id, jugador);
    }

}
