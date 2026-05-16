package com.safuhost.backend.servicio;

import com.safuhost.backend.repositorio.RepositorioServidor;
import com.github.dockerjava.api.DockerClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.safuhost.backend.modelo.Servidor;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class ServicioServidor {

    @Autowired
    private RepositorioServidor repositorio;

    @Autowired
    private DockerClient dockerClient;

    @Autowired
    private ServicioPuertos servicioPuertos;

    @Autowired
    private ServicioDocker servicioDocker;

    @Autowired
    private ServicioMods servicioMods;

    @Autowired
    private ServicioWhitelist servicioWhitelist;

    @Autowired
    private ServicioUsuario servicioUsuario;

    @Autowired
    private ServicioUpnp servicioUpnp;

    public Servidor crearServidor(Servidor nuevo) {
        if (repositorio.existsByNombre(nuevo.getNombre())) {
            throw new RuntimeException("¡Error! Ya existe un servidor con el nombre: " + nuevo.getNombre());
        }

        nuevo.setPropietario(servicioUsuario.getUsuarioActual());
        Integer puertoLibre = servicioPuertos.encontrarPuertoLibre();
        nuevo.setPuerto(puertoLibre);

        String rutaLocal = "C:/mc-servers/" + nuevo.getNombre();
        String tagJava   = resolverTagJava(nuevo.getVersion());

        try {
            dockerClient.pullImageCmd("itzg/minecraft-server")
                    .withTag(tagJava).start().awaitCompletion();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("La descarga de la imagen fue interrumpida", e);
        }

        CreateContainerResponse container = crearContenedorDocker(nuevo, tagJava, rutaLocal);

        if ("FORGE".equals(nuevo.getTipo()) || "FABRIC".equals(nuevo.getTipo())) {
            if ("FABRIC".equals(nuevo.getTipo())) {
                servicioMods.instalarFabricApi(nuevo.getNombre(), nuevo.getVersion());
            }
            if (nuevo.getModIniciales() != null) {
                for (String modId : nuevo.getModIniciales()) {
                    try {
                        String jar = servicioMods.instalarModEnCarpeta(nuevo.getNombre(), nuevo.getTipo(), nuevo.getVersion(), modId);
                        System.out.println("[Mods] Instalado: " + jar);
                    } catch (Exception e) {
                        System.err.println("[Mods] FALLO al instalar mod " + modId + ": " + e.getMessage());
                    }
                }
            }
        }

        dockerClient.startContainerCmd(container.getId()).exec();

        servicioUpnp.abrirPuerto(puertoLibre);
        final int puertoFinal = puertoLibre;
        CompletableFuture.delayedExecutor(10, TimeUnit.SECONDS).execute(() -> {
            System.out.println("[UPnP] Revalidando puerto " + puertoFinal + "...");
            servicioUpnp.abrirPuerto(puertoFinal);
        });

        nuevo.setIdContenedor(container.getId());
        nuevo.setEstado("INICIANDO");
        return repositorio.save(nuevo);
    }

    public List<Servidor> listarTodos() {
        return repositorio.findByPropietario(servicioUsuario.getUsuarioActual());
    }

    public Servidor obtenerPorId(Long id) {
        Servidor s = repositorio.findById(id)
                .orElseThrow(() -> new RuntimeException("No existe ningún servidor con el id: " + id));
        servicioUsuario.verificarPropiedad(s);
        return s;
    }

    public Servidor pararServidor(Long id) {
        Servidor s = obtenerPorId(id);
        dockerClient.stopContainerCmd(s.getIdContenedor()).exec();
        servicioUpnp.cerrarPuerto(s.getPuerto());
        s.setEstado("APAGADO");
        return repositorio.save(s);
    }

    public Servidor iniciarServidor(Long id) {
        Servidor s = obtenerPorId(id);
        dockerClient.startContainerCmd(s.getIdContenedor()).exec();
        servicioUpnp.abrirPuerto(s.getPuerto());
        final int puerto = s.getPuerto();
        CompletableFuture.delayedExecutor(10, TimeUnit.SECONDS).execute(() -> {
            System.out.println("[UPnP] Revalidando puerto " + puerto + "...");
            servicioUpnp.abrirPuerto(puerto);
        });
        s.setEstado("INICIANDO");
        return repositorio.save(s);
    }

    public void eliminarServidor(Long id) {
        Servidor s = obtenerPorId(id);
        try { dockerClient.stopContainerCmd(s.getIdContenedor()).exec(); } catch (RuntimeException ignored) {}
        try { dockerClient.removeContainerCmd(s.getIdContenedor()).exec(); } catch (RuntimeException ignored) {}
        servicioUpnp.cerrarPuerto(s.getPuerto());
        repositorio.deleteById(id);
        java.io.File carpeta = new java.io.File("C:/mc-servers/" + s.getNombre());
        if (carpeta.exists()) {
            try {
                org.apache.commons.io.FileUtils.deleteDirectory(carpeta);
                System.out.println("[Servidor] Carpeta eliminada: " + carpeta.getPath());
            } catch (java.io.IOException e) {
                System.err.println("[Servidor] No se pudo borrar: " + carpeta.getPath());
            }
        }
    }

    public Servidor actualizarServidor(Long id, Servidor datos) {
        Servidor s = obtenerPorId(id);

        // Guardar valores antiguos para comparar
        String dificultadAntigua    = s.getDificultad();
        String modoJuegoAntiguo     = s.getModoJuego();
        String adminsAntiguos       = s.getAdministradores();
        boolean whitelistAntigua    = s.isUsarWhitelist();
        String listaBlancaAntigua   = s.getListaBlanca();

        s.setNombre(datos.getNombre());
        s.setDificultad(datos.getDificultad());
        s.setModoJuego(datos.getModoJuego());
        s.setPvp(datos.isPvp());
        s.setUsarWhitelist(datos.isUsarWhitelist());
        s.setListaBlanca(datos.getListaBlanca());
        s.setAdministradores(datos.getAdministradores());
        s.setModoOnline(datos.isModoOnline());
        s.setUrlIcono(datos.getUrlIcono());
        Servidor guardado = repositorio.save(s);

        // Aplicar cambios en vivo al contenedor en marcha
        aplicarCambiosEnVivo(guardado, dificultadAntigua, modoJuegoAntiguo, adminsAntiguos, whitelistAntigua, listaBlancaAntigua);

        return guardado;
    }

    /** Envía comandos al servidor Minecraft en marcha para reflejar los cambios sin reiniciar */
    private void aplicarCambiosEnVivo(Servidor s, String dificultadAntigua, String modoJuegoAntiguo,
                                       String adminsAntiguos, boolean whitelistAntigua, String listaBlancaAntigua) {
        if (s.getIdContenedor() == null) return;

        // Dificultad
        if (s.getDificultad() != null && !s.getDificultad().equals(dificultadAntigua)) {
            enviarComandoSeguro(s, "difficulty " + s.getDificultad());
        }

        // Modo de juego: cambia el por defecto (para nuevos) + a todos los conectados
        if (s.getModoJuego() != null && !s.getModoJuego().equals(modoJuegoAntiguo)) {
            enviarComandoSeguro(s, "defaultgamemode " + s.getModoJuego());
            enviarComandoSeguro(s, "gamemode " + s.getModoJuego() + " @a");
        }

        // OPs: comparar listas y aplicar diferencias
        java.util.Set<String> opsViejos  = parsearLista(adminsAntiguos);
        java.util.Set<String> opsNuevos  = parsearLista(s.getAdministradores());
        for (String op : opsNuevos) {
            if (!opsViejos.contains(op)) enviarComandoSeguro(s, "op " + op);
        }
        for (String op : opsViejos) {
            if (!opsNuevos.contains(op)) enviarComandoSeguro(s, "deop " + op);
        }

        // Whitelist on/off
        if (s.isUsarWhitelist() != whitelistAntigua) {
            enviarComandoSeguro(s, "whitelist " + (s.isUsarWhitelist() ? "on" : "off"));
        }

        // Lista blanca: comparar y aplicar diferencias
        java.util.Set<String> wlVieja = parsearLista(listaBlancaAntigua);
        java.util.Set<String> wlNueva = parsearLista(s.getListaBlanca());
        for (String j : wlNueva) {
            if (!wlVieja.contains(j)) enviarComandoSeguro(s, "whitelist add " + j);
        }
        for (String j : wlVieja) {
            if (!wlNueva.contains(j)) enviarComandoSeguro(s, "whitelist remove " + j);
        }
    }

    private java.util.Set<String> parsearLista(String csv) {
        java.util.Set<String> set = new java.util.HashSet<>();
        if (csv == null || csv.isBlank()) return set;
        for (String x : csv.split(",")) {
            String limpio = x.trim();
            if (!limpio.isEmpty()) set.add(limpio);
        }
        return set;
    }

    private void enviarComandoSeguro(Servidor s, String comando) {
        try {
            servicioDocker.ejecutarComandoEnContenedor(s.getIdContenedor(), comando);
            System.out.println("[En vivo] '" + s.getNombre() + "' → " + comando);
        } catch (Exception e) {
            System.err.println("[En vivo] No se pudo enviar '" + comando + "' a '" + s.getNombre() + "': " + e.getMessage());
        }
    }

    public String obtenerEstado(Long id) {
        return servicioDocker.obtenerEstado(id);
    }

    public void enviarComandoConsola(Long id, String comando) {
        Servidor s = obtenerPorId(id);
        servicioDocker.ejecutarComandoEnContenedor(s.getIdContenedor(), comando);
    }

    public void resetearMundo(Long id) {
        servicioMods.resetearMundo(id);
    }

    public List<String> listarMods(Long id) {
        return servicioMods.listarMods(id);
    }

    public Object buscarModsModrinth(String query) {
        return servicioMods.buscarModsModrinth(query);
    }

    public Map<String, Object> verificarCompatibilidadMod(String modId, String tipo, String version) {
        return servicioMods.verificarCompatibilidadMod(modId, tipo, version);
    }

    public String instalarModModrinth(Long id, String modrinthId) throws java.io.IOException {
        String jar = servicioMods.instalarModModrinth(id, modrinthId);
        Servidor s = obtenerPorId(id);
        try { dockerClient.stopContainerCmd(s.getIdContenedor()).exec(); } catch (Exception ignored) {}
        try {
            dockerClient.startContainerCmd(s.getIdContenedor()).exec();
            s.setEstado("INICIANDO");
            repositorio.save(s);
            servicioUpnp.abrirPuerto(s.getPuerto());
            System.out.println("[Mods] Servidor '" + s.getNombre() + "' reiniciado tras instalar " + jar);
        } catch (Exception e) {
            System.err.println("[Mods] No se pudo reiniciar: " + e.getMessage());
        }
        return jar;
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

    private CreateContainerResponse crearContenedorDocker(Servidor s, String tagJava, String rutaLocal) {
        ExposedPort puertoInterno = ExposedPort.tcp(25565);
        Ports portBindings = new Ports();
        portBindings.bind(puertoInterno, Ports.Binding.bindPort(s.getPuerto()));
        return dockerClient.createContainerCmd("itzg/minecraft-server:" + tagJava)
                .withName("mc-" + s.getNombre())
                .withEnv(buildEnv(s))
                .withHostConfig(HostConfig.newHostConfig()
                        .withPortBindings(portBindings)
                        .withBinds(new com.github.dockerjava.api.model.Bind(
                                rutaLocal, new com.github.dockerjava.api.model.Volume("/data"))))
                .exec();
    }

    private List<String> buildEnv(Servidor s) {
        List<String> env = new ArrayList<>();
        env.add("EULA=TRUE");
        env.add("VERSION=" + s.getVersion());
        env.add("TYPE=" + s.getTipo());
        env.add("DIFFICULTY=" + s.getDificultad());
        env.add("MODE=" + s.getModoJuego());
        env.add("PVP=" + s.isPvp());
        env.add("ONLINE_MODE=" + s.isModoOnline());
        env.add("ENABLE_WHITELIST=" + s.isUsarWhitelist());
        if (s.isUsarWhitelist() && s.getListaBlanca() != null && !s.getListaBlanca().isEmpty()) {
            env.add("WHITELIST=" + s.getListaBlanca());
        }
        env.add("OPS=" + (s.getAdministradores() != null ? s.getAdministradores() : ""));
        env.add("AUTOPAUSE=false");
        env.add("AUTOPAUSE_TIMEOUT_EST=300");
        env.add("CREATE_CONSOLE_IN_PIPE=true");
        return env;
    }

    private String resolverTagJava(String version) {
        if (version == null || version.equalsIgnoreCase("LATEST")) return "java21";
        try {
            String[] partes = version.split("\\.");
            int minor = Integer.parseInt(partes.length > 1 ? partes[1] : "21");
            if (minor <= 11) return "java8";
            if (minor <= 16) return "java11";
        } catch (NumberFormatException ignored) {}
        return "java21";
    }
}
