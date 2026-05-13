package com.safuhost.backend.servicio;

import com.safuhost.backend.modelo.Servidor;
import com.safuhost.backend.repositorio.RepositorioServidor;
import com.github.dockerjava.api.DockerClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ServicioDocker {

    @Autowired
    private RepositorioServidor repositorio;

    @Autowired
    private DockerClient dockerClient;

    @Autowired
    private ServicioUsuario servicioUsuario;

    public String obtenerEstado(Long id) {
        Servidor servidor = repositorio.findById(id)
                .orElseThrow(() -> new RuntimeException("No existe ningún servidor con el id: " + id));
        servicioUsuario.verificarPropiedad(servidor);

        List<com.github.dockerjava.api.model.Container> contenedores = dockerClient
                .listContainersCmd()
                .withShowAll(true)
                .withIdFilter(java.util.Collections.singletonList(servidor.getIdContenedor()))
                .exec();

        if (contenedores.isEmpty()) {
            return "DESCONOCIDO";
        }

        String estadoDocker = contenedores.get(0).getState();
        String estadoApp;
        switch (estadoDocker) {
            case "running" -> estadoApp = "EN_LINEA";
            case "exited"  -> estadoApp = "APAGADO";
            case "paused"  -> estadoApp = "PAUSADO";
            default        -> estadoApp = "DESCONOCIDO";
        }

        servidor.setEstado(estadoApp);
        repositorio.save(servidor);

        return estadoApp;
    }

    public void ejecutarComandoEnContenedor(String idContenedor, String comando) {
        try {
            String[] partes = comando.split(" ");
            String[] cmdCompleto = new String[partes.length + 1];
            cmdCompleto[0] = "mc-send-to-console";
            System.arraycopy(partes, 0, cmdCompleto, 1, partes.length);

            com.github.dockerjava.api.command.ExecCreateCmdResponse exec = dockerClient
                    .execCreateCmd(idContenedor)
                    .withUser("1000")
                    .withCmd(cmdCompleto)
                    .exec();

            dockerClient.execStartCmd(exec.getId())
                    .exec(new com.github.dockerjava.core.command.ExecStartResultCallback())
                    .awaitCompletion();
        } catch (Exception e) {
            throw new RuntimeException("No se pudo ejecutar el comando en el contenedor: " + e.getMessage());
        }
    }
}
