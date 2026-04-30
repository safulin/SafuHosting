package com.safuhost.backend.servicio;

import com.safuhost.backend.repositorio.RepositorioServidor;
import com.github.dockerjava.api.DockerClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.ServerSocket;

@Service
public class ServicioServidor {

    @Autowired
    private RepositorioServidor repositorio;

    @Autowired
    private DockerClient dockerClient;

    // Esta función busca el siguiente puerto libre
    public Integer encontrarPuertoLibre() {
        int puerto = 25565; // Puerto base de Minecraft

        while (true) {

            // Comprobamos si el puerto está siendo usado por otro programa en Windows
            if (puertoDisponibleEnSistema(puerto)) {
                return puerto;
            }
            puerto++;
        }
    }

    private boolean puertoDisponibleEnSistema(int puerto) {
        try (ServerSocket serverSocket = new ServerSocket(puerto)) {
            return true; // Si puede abrir el socket, el puerto está libre
        } catch (IOException e) {
            return false; // Si da error, es que alguien ya lo está usando
        }
    }
}