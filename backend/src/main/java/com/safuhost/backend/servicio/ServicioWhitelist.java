package com.safuhost.backend.servicio;

import com.safuhost.backend.modelo.Servidor;
import com.safuhost.backend.repositorio.RepositorioServidor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class ServicioWhitelist {

    @Autowired
    private RepositorioServidor repositorio;

    @Autowired
    private ServicioDocker servicioDocker;

    @Autowired
    private ServicioUsuario servicioUsuario;

    public List<String> obtenerWhitelist(Long id) {
        Servidor servidor = repositorio.findById(id)
                .orElseThrow(() -> new RuntimeException("No existe ningún servidor con el id: " + id));
        servicioUsuario.verificarPropiedad(servidor);

        if (servidor.getListaBlanca() == null || servidor.getListaBlanca().isBlank()) {
            return new ArrayList<>();
        }

        return Arrays.asList(servidor.getListaBlanca().split(","));
    }

    public Servidor añadirAWhitelist(Long id, String jugador) {
        Servidor servidor = repositorio.findById(id)
                .orElseThrow(() -> new RuntimeException("No existe ningún servidor con el id: " + id));
        servicioUsuario.verificarPropiedad(servidor);

        String listaActual = servidor.getListaBlanca();
        if (listaActual == null || listaActual.isBlank()) {
            servidor.setListaBlanca(jugador);
        } else if (!listaActual.contains(jugador)) {
            servidor.setListaBlanca(listaActual + "," + jugador);
        }

        if ("EN_LINEA".equals(servidor.getEstado())) {
            servicioDocker.ejecutarComandoEnContenedor(servidor.getIdContenedor(), "whitelist add " + jugador);
        }

        return repositorio.save(servidor);
    }

    public Servidor quitarDeWhitelist(Long id, String jugador) {
        Servidor servidor = repositorio.findById(id)
                .orElseThrow(() -> new RuntimeException("No existe ningún servidor con el id: " + id));
        servicioUsuario.verificarPropiedad(servidor);

        if (servidor.getListaBlanca() != null) {
            List<String> lista = new ArrayList<>(Arrays.asList(servidor.getListaBlanca().split(",")));
            lista.remove(jugador);
            servidor.setListaBlanca(String.join(",", lista));
        }

        if ("EN_LINEA".equals(servidor.getEstado())) {
            servicioDocker.ejecutarComandoEnContenedor(servidor.getIdContenedor(), "whitelist remove " + jugador);
        }

        return repositorio.save(servidor);
    }
}
