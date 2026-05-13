package com.safuhost.backend.servicio;

import com.safuhost.backend.repositorio.RepositorioServidor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.ServerSocket;

@Service
public class ServicioPuertos {

    @Autowired
    private RepositorioServidor repositorio;

    public Integer encontrarPuertoLibre() {
        int puerto = 25565;

        while (true) {
            boolean ocupadoEnBBDD = repositorio.existsByPuerto(puerto);
            boolean ocupadoEnSistema = !puertoDisponibleEnSistema(puerto);

            if (!ocupadoEnBBDD && !ocupadoEnSistema) {
                return puerto;
            }

            puerto++;
        }
    }

    private boolean puertoDisponibleEnSistema(int puerto) {
        try (ServerSocket serverSocket = new ServerSocket(puerto)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
