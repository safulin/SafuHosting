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

    public String obtenerEstado(Long id) {
        // 1. Buscamos el servidor en SQLite para obtener su idContenedor
        Servidor servidor = obtenerPorId(id);

        // 2. Preguntamos a Docker el estado real del contenedor
        // Usamos listContainersCmd en vez de inspectContainerCmd porque inspect intenta parsear
        // los volúmenes y falla con rutas de Windows (C:/mc-servers/... tiene dos puntos)
        // withShowAll(true) es necesario para que también devuelva contenedores parados, no solo los activos
        List<com.github.dockerjava.api.model.Container> contenedores = dockerClient
                .listContainersCmd()
                .withShowAll(true)
                .withIdFilter(java.util.Collections.singletonList(servidor.getIdContenedor()))
                .exec();

        // 3. Si Docker no encuentra el contenedor devolvemos DESCONOCIDO
        if (contenedores.isEmpty()) {
            return "DESCONOCIDO";
        }

        // 4. Traducimos el estado de Docker a los estados que usa nuestra app
        // getState() devuelve: "running", "exited", "paused", "created"...
        String estadoDocker = contenedores.get(0).getState();
        String estadoApp;
        switch (estadoDocker) {
            case "running" -> estadoApp = "EN_LINEA";
            case "exited"  -> estadoApp = "APAGADO";
            case "paused"  -> estadoApp = "PAUSADO";
            default        -> estadoApp = "DESCONOCIDO";
        }

        // 5. Sincronizamos el estado en SQLite por si estaba desactualizado
        servidor.setEstado(estadoApp);
        repositorio.save(servidor);

        return estadoApp;
    }

    public List<String> obtenerWhitelist(Long id) {
        Servidor servidor = obtenerPorId(id);

        // Si la lista está vacía o es null devolvemos una lista vacía
        if (servidor.getListaBlanca() == null || servidor.getListaBlanca().isBlank()) {
            return new ArrayList<>();
        }

        // La listaBlanca se guarda como "Jugador1,Jugador2,Jugador3"
        // La dividimos por comas y devolvemos cada nombre como elemento de la lista
        return Arrays.asList(servidor.getListaBlanca().split(","));
    }

    public Servidor añadirAWhitelist(Long id, String jugador) {
        Servidor servidor = obtenerPorId(id);

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
        // así no hace falta reiniciarlo para que surta efecto
        if ("EN_LINEA".equals(servidor.getEstado())) {
            ejecutarComandoEnContenedor(servidor.getIdContenedor(), "whitelist add " + jugador);
        }

        return repositorio.save(servidor);
    }

    public Servidor quitarDeWhitelist(Long id, String jugador) {
        Servidor servidor = obtenerPorId(id);

        // Filtramos la lista quitando el jugador que queremos eliminar
        if (servidor.getListaBlanca() != null) {
            List<String> lista = new ArrayList<>(Arrays.asList(servidor.getListaBlanca().split(",")));
            lista.remove(jugador);
            servidor.setListaBlanca(String.join(",", lista));
        }

        // Si el servidor está en línea le mandamos el comando directamente a Docker
        if ("EN_LINEA".equals(servidor.getEstado())) {
            ejecutarComandoEnContenedor(servidor.getIdContenedor(), "whitelist remove " + jugador);
        }

        return repositorio.save(servidor);
    }

    public List<String> listarMods(Long id) {
        Servidor servidor = obtenerPorId(id);

        // La carpeta mods está dentro del directorio del servidor que se montó como volumen al crearlo
        java.io.File carpetaMods = new java.io.File("C:/mc-servers/" + servidor.getNombre() + "/mods");

        // Si la carpeta no existe (todavía no se ha subido ningún mod) devolvemos una lista vacía
        if (!carpetaMods.exists() || !carpetaMods.isDirectory()) {
            return new ArrayList<>();
        }

        // Filtramos solo los archivos .jar (los mods de Minecraft son siempre .jar)
        java.io.File[] archivos = carpetaMods.listFiles((directorio, nombre) -> nombre.endsWith(".jar"));
        if (archivos == null) {
            return new ArrayList<>();
        }

        List<String> nombres = new ArrayList<>();
        for (java.io.File archivo : archivos) {
            nombres.add(archivo.getName());
        }
        return nombres;
    }

    public Object buscarModsModrinth(String query) {
        // RestTemplate es la clase de Spring para hacer peticiones HTTP a APIs externas
        // Lo usamos para llamar a la API pública y gratuita de Modrinth
        org.springframework.web.client.RestTemplate http = new org.springframework.web.client.RestTemplate();

        // limit=20 para no traer demasiados resultados de golpe
        String url = "https://api.modrinth.com/v2/search?query=" + query + "&limit=20";

        // getForObject hace un GET y nos devuelve directamente el JSON convertido en un objeto
        return http.getForObject(url, Object.class);
    }

    public String instalarModModrinth(Long id, String modrinthId) throws java.io.IOException {
        Servidor servidor = obtenerPorId(id);

        // Solo permitimos servidores FORGE o FABRIC porque vanilla no soporta mods
        if (!"FORGE".equals(servidor.getTipo()) && !"FABRIC".equals(servidor.getTipo())) {
            throw new RuntimeException("Solo se pueden instalar mods en servidores FORGE o FABRIC, este servidor es " + servidor.getTipo());
        }

        org.springframework.web.client.RestTemplate http = new org.springframework.web.client.RestTemplate();

        // 1. Pedimos a Modrinth las versiones disponibles de este mod filtradas por loader y versión MC
        // El loader debe ir en minúsculas (forge / fabric) entre comillas y entre corchetes (formato JSON array)
        String loader = servidor.getTipo().toLowerCase();
        String versionMC = servidor.getVersion();
        String urlVersiones = "https://api.modrinth.com/v2/project/" + modrinthId + "/version"
                + "?loaders=[\"" + loader + "\"]"
                + "&game_versions=[\"" + versionMC + "\"]";

        List<java.util.Map<String, Object>> versiones = http.getForObject(urlVersiones, List.class);

        if (versiones == null || versiones.isEmpty()) {
            throw new RuntimeException("No hay ninguna versión de este mod compatible con " + loader + " " + versionMC);
        }

        // 2. Cogemos la primera versión (la más reciente compatible) y dentro de ella el primer archivo .jar
        java.util.Map<String, Object> primeraVersion = versiones.get(0);
        List<java.util.Map<String, Object>> archivos = (List<java.util.Map<String, Object>>) primeraVersion.get("files");
        java.util.Map<String, Object> archivo = archivos.get(0);

        String urlDescarga = (String) archivo.get("url");
        String nombreArchivo = (String) archivo.get("filename");

        // 3. Creamos la carpeta mods si no existe
        java.io.File carpetaMods = new java.io.File("C:/mc-servers/" + servidor.getNombre() + "/mods");
        if (!carpetaMods.exists()) {
            carpetaMods.mkdirs();
        }

        // 4. Descargamos el archivo .jar desde el CDN de Modrinth y lo guardamos en disco
        // getForObject con byte[].class nos devuelve el contenido binario del archivo
        byte[] contenido = http.getForObject(urlDescarga, byte[].class);
        java.io.File destino = new java.io.File(carpetaMods, nombreArchivo);
        java.nio.file.Files.write(destino.toPath(), contenido);

        return nombreArchivo;
    }

    public void eliminarMod(Long id, String nombreMod) {
        Servidor servidor = obtenerPorId(id);

        // Validamos el nombre por seguridad igual que al subir
        if (nombreMod.contains("..") || nombreMod.contains("/") || nombreMod.contains("\\")) {
            throw new RuntimeException("Nombre de archivo inválido");
        }

        java.io.File mod = new java.io.File("C:/mc-servers/" + servidor.getNombre() + "/mods/" + nombreMod);
        if (!mod.exists()) {
            throw new RuntimeException("No existe el mod: " + nombreMod);
        }

        if (!mod.delete()) {
            throw new RuntimeException("No se pudo eliminar el mod: " + nombreMod);
        }
    }

    public void enviarComandoConsola(Long id, String comando) {
        Servidor servidor = obtenerPorId(id);
        ejecutarComandoEnContenedor(servidor.getIdContenedor(), comando);
    }

    private void ejecutarComandoEnContenedor(String idContenedor, String comando) {
        // execCreateCmd crea el comando dentro del contenedor pero no lo ejecuta todavía
        // mc-send-to-console es un script que viene dentro de la imagen itzg/minecraft-server
        // y sirve para mandar comandos directamente a la consola de Minecraft
        try {
            // Dividimos el comando por espacios para pasarlo como argumentos separados a mc-send-to-console
            // Por ejemplo "say Hola" se convierte en {"mc-send-to-console", "say", "Hola"}
            String[] partes = comando.split(" ");
            String[] cmdCompleto = new String[partes.length + 1];
            cmdCompleto[0] = "mc-send-to-console";
            System.arraycopy(partes, 0, cmdCompleto, 1, partes.length);

            com.github.dockerjava.api.command.ExecCreateCmdResponse exec = dockerClient
                    .execCreateCmd(idContenedor)
                    .withUser("1000") // la imagen itzg/minecraft-server exige que los exec se ejecuten como user 1000
                    .withCmd(cmdCompleto)
                    .exec();

            // execStartCmd ejecuta el comando que acabamos de crear
            dockerClient.execStartCmd(exec.getId())
                    .exec(new com.github.dockerjava.core.command.ExecStartResultCallback())
                    .awaitCompletion();
        } catch (Exception e) {
            // Si falla el comando en Docker no interrumpimos el flujo,
            // el cambio ya quedó guardado en SQLite igualmente
            throw new RuntimeException("No se pudo ejecutar el comando en el contenedor: " + e.getMessage());
        }
    }

}