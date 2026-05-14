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

    public Servidor crearServidor(Servidor nuevo) {

        if (repositorio.existsByNombre(nuevo.getNombre())) {
            throw new RuntimeException("¡Error! Ya existe un servidor con el nombre: " + nuevo.getNombre());
        }

        nuevo.setPropietario(servicioUsuario.getUsuarioActual());

        Integer puertoLibre = servicioPuertos.encontrarPuertoLibre();
        nuevo.setPuerto(puertoLibre);

        String rutaLocal = "C:/mc-servers/" + nuevo.getNombre();

        List<String> env = new ArrayList<>();
        env.add("EULA=TRUE");
        env.add("VERSION=" + nuevo.getVersion());
        env.add("TYPE=" + nuevo.getTipo());
        env.add("DIFFICULTY=" + nuevo.getDificultad());
        env.add("MODE=" + nuevo.getModoJuego());
        env.add("PVP=" + nuevo.isPvp());
        env.add("ONLINE_MODE=" + nuevo.isModoOnline());
        env.add("ENABLE_WHITELIST=" + nuevo.isUsarWhitelist());
        if (nuevo.isUsarWhitelist() && nuevo.getListaBlanca() != null && !nuevo.getListaBlanca().isEmpty()) {
            env.add("WHITELIST=" + nuevo.getListaBlanca());
        }
        env.add("OPS=" + nuevo.getAdministradores());
        env.add("AUTOPAUSE=false");
        env.add("AUTOPAUSE_TIMEOUT_EST=300");
        env.add("CREATE_CONSOLE_IN_PIPE=true");

        ExposedPort puertoInterno = ExposedPort.tcp(25565);
        Ports portBindings = new Ports();
        portBindings.bind(puertoInterno, Ports.Binding.bindPort(puertoLibre));

        String tagJava = resolverTagJava(nuevo.getVersion());

        try {
            dockerClient.pullImageCmd("itzg/minecraft-server")
                    .withTag(tagJava)
                    .start()
                    .awaitCompletion();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("La descarga de la imagen de Minecraft fue interrumpida", e);
        }

        CreateContainerResponse container = dockerClient.createContainerCmd("itzg/minecraft-server:" + tagJava)
                .withName("mc-" + nuevo.getNombre())
                .withEnv(env)
                .withHostConfig(HostConfig.newHostConfig()
                        .withPortBindings(portBindings)
                        .withBinds(new com.github.dockerjava.api.model.Bind(rutaLocal, new com.github.dockerjava.api.model.Volume("/data"))))
                .exec();

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
                        e.printStackTrace();
                    }
                }
            }
        }

        dockerClient.startContainerCmd(container.getId()).exec();

        nuevo.setIdContenedor(container.getId());
        nuevo.setEstado("INICIANDO");
        return repositorio.save(nuevo);
    }

    public List<Servidor> listarTodos() {
        return repositorio.findByPropietario(servicioUsuario.getUsuarioActual());
    }

    public Servidor obtenerPorId(Long id) {
        Servidor servidor = repositorio.findById(id)
                .orElseThrow(() -> new RuntimeException("No existe ningún servidor con el id: " + id));
        servicioUsuario.verificarPropiedad(servidor);
        return servidor;
    }

    public Servidor pararServidor(Long id) {
        Servidor servidor = obtenerPorId(id);
        dockerClient.stopContainerCmd(servidor.getIdContenedor()).exec();
        servidor.setEstado("APAGADO");
        return repositorio.save(servidor);
    }

    public Servidor iniciarServidor(Long id) {
        Servidor servidor = obtenerPorId(id);
        dockerClient.startContainerCmd(servidor.getIdContenedor()).exec();
        servidor.setEstado("INICIANDO");
        return repositorio.save(servidor);
    }

    public void eliminarServidor(Long id) {
        Servidor servidor = obtenerPorId(id);

        try {
            dockerClient.stopContainerCmd(servidor.getIdContenedor()).exec();
        } catch (RuntimeException e) {
        }

        try {
            dockerClient.removeContainerCmd(servidor.getIdContenedor()).exec();
        } catch (RuntimeException e) {
        }

        repositorio.deleteById(id);
    }

    public Servidor actualizarServidor(Long id, Servidor datos) {
        Servidor servidor = obtenerPorId(id);

        servidor.setNombre(datos.getNombre());
        servidor.setDificultad(datos.getDificultad());
        servidor.setModoJuego(datos.getModoJuego());
        servidor.setPvp(datos.isPvp());
        servidor.setUsarWhitelist(datos.isUsarWhitelist());
        servidor.setListaBlanca(datos.getListaBlanca());
        servidor.setAdministradores(datos.getAdministradores());
        servidor.setModoOnline(datos.isModoOnline());
        servidor.setUrlIcono(datos.getUrlIcono());

        return repositorio.save(servidor);
    }

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

    public java.util.Map<String, Object> verificarCompatibilidadMod(String modId, String tipo, String version) {
        return servicioMods.verificarCompatibilidadMod(modId, tipo, version);
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

    private String resolverTagJava(String version) {
        if (version == null) return "java21";
        try {
            String[] partes = version.split("\\.");
            int minor = Integer.parseInt(partes.length > 1 ? partes[1] : "0");
            if (minor <= 11) return "java8";
            if (minor <= 16) return "java11";
        } catch (NumberFormatException ignored) {}
        return "java21";
    }
}
