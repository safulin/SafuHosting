package com.safuhost.backend.servicio;

import com.safuhost.backend.modelo.Servidor;
import com.safuhost.backend.repositorio.RepositorioServidor;
import com.github.dockerjava.api.DockerClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ServicioDocker {

    // Conexión con nuestra base de datos (SQLite)
    @Autowired
    private RepositorioServidor repositorio;

    // con el autowired tambien llamamos al dockerclient que esta creado en el configuracion/configuraciondocker, usa una libreria para hablar con docker
    // desde spring, es como un mando a distancia
    @Autowired
    private DockerClient dockerClient;

    public String obtenerEstado(Long id) {
        // 1. Buscamos el servidor en SQLite para obtener su idContenedor
        Servidor servidor = repositorio.findById(id)
                .orElseThrow(() -> new RuntimeException("No existe ningún servidor con el id: " + id));

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

    public void ejecutarComandoEnContenedor(String idContenedor, String comando) {
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
