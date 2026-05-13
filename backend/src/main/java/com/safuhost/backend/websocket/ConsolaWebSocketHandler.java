package com.safuhost.backend.websocket;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import com.safuhost.backend.modelo.Servidor;
import com.safuhost.backend.repositorio.RepositorioServidor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConsolaWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private DockerClient dockerClient;

    @Autowired
    private RepositorioServidor repositorio;

    private final ConcurrentHashMap<String, Closeable> streamsPorSesion = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession sesion) throws Exception {
        String ruta = sesion.getUri().getPath();
        Long id = Long.parseLong(ruta.substring(ruta.lastIndexOf('/') + 1));

        Servidor servidor = repositorio.findById(id)
                .orElseThrow(() -> new RuntimeException("No existe ningún servidor con el id: " + id));

        Closeable stream = dockerClient.logContainerCmd(servidor.getIdContenedor())
                .withStdOut(true)
                .withStdErr(true)
                .withFollowStream(true)
                .withTail(50)
                .exec(new ResultCallback.Adapter<Frame>() {
                    @Override
                    public void onNext(Frame frame) {
                        try {
                            if (sesion.isOpen()) {
                                String linea = new String(frame.getPayload()).trim();
                                if (!linea.isEmpty()) {
                                    sesion.sendMessage(new TextMessage(linea));
                                }
                            }
                        } catch (IOException e) {
                        }
                    }
                });

        streamsPorSesion.put(sesion.getId(), stream);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession sesion, CloseStatus estado) throws Exception {
        Closeable stream = streamsPorSesion.remove(sesion.getId());
        if (stream != null) {
            stream.close();
        }
    }
}
