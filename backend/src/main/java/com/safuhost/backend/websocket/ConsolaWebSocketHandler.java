package com.safuhost.backend.websocket;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame; // representa cada línea que genera el contenedor. Docker no envía texto plano, envía "frames" que contienen el texto más metadatos
import com.safuhost.backend.modelo.Servidor;
import com.safuhost.backend.servicio.ServicioServidor;
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
    private ServicioServidor servicio;

    // Guardamos el stream activo de cada sesión para poder cerrarlo cuando el usuario desconecte
    // ConcurrentHashMap es como un HashMap normal pero seguro para múltiples usuarios a la vez
    private final ConcurrentHashMap<String, Closeable> streamsPorSesion = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession sesion) throws Exception {
        // Extraemos el id del servidor de la URL: /ws/consola/1 → 1
        String ruta = sesion.getUri().getPath();
        Long id = Long.parseLong(ruta.substring(ruta.lastIndexOf('/') + 1));

        Servidor servidor = servicio.obtenerPorId(id);

        // Arrancamos el stream de logs de Docker
        // withFollowStream(true) → se queda escuchando continuamente, no para al llegar al final
        // withStdOut / withStdErr → capturamos tanto la salida normal como los errores
        // withTail(50) → al conectarse el frontend recibe las últimas 50 líneas del historial
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
                                // Cada línea que genera el servidor de Minecraft se envía al frontend por WebSocket
                                String linea = new String(frame.getPayload()).trim();
                                if (!linea.isEmpty()) {
                                    sesion.sendMessage(new TextMessage(linea));
                                }
                            }
                        } catch (IOException e) {
                            // La sesión se cerró mientras enviábamos, lo ignoramos
                        }
                    }
                });

        // Guardamos el stream asociado a esta sesión para cerrarlo después
        streamsPorSesion.put(sesion.getId(), stream);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession sesion, CloseStatus estado) throws Exception {
        // Cuando el usuario cierra la consola paramos el stream de Docker para no desperdiciar recursos
        Closeable stream = streamsPorSesion.remove(sesion.getId());
        if (stream != null) {
            stream.close();
        }
    }
}
