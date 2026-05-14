package com.safuhost.backend.servicio;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerPort;
import com.safuhost.backend.repositorio.RepositorioServidor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ServicioPuertos {

    @Autowired
    private RepositorioServidor repositorio;

    @Autowired
    private DockerClient dockerClient;

    public Integer encontrarPuertoLibre() {
        Set<Integer> puertosDocker = obtenerPuertosUsadosPorDocker();
        int puerto = 25565;

        while (true) {
            boolean ocupadoEnBBDD = repositorio.existsByPuerto(puerto);
            boolean ocupadoEnDocker = puertosDocker.contains(puerto);

            if (!ocupadoEnBBDD && !ocupadoEnDocker) {
                return puerto;
            }

            puerto++;
        }
    }

    private Set<Integer> obtenerPuertosUsadosPorDocker() {
        Set<Integer> puertos = new HashSet<>();
        try {
            List<Container> contenedores = dockerClient.listContainersCmd()
                    .withShowAll(true)
                    .exec();
            for (Container c : contenedores) {
                for (ContainerPort p : c.getPorts()) {
                    if (p.getPublicPort() != null) {
                        puertos.add(p.getPublicPort());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Puertos] No se pudo consultar Docker: " + e.getMessage());
        }
        return puertos;
    }
}
