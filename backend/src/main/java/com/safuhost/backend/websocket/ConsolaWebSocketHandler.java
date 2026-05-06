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

    // es un hashmap que guarda de quien es cada consola para cuando un usuario cierre su consola saber cual es el stream de docker que hay que cerrar
    // ConcurrentHashMap en lugar de HashMap normal porque pueden conectarse varios usuarios a la vez,
    // cada uno en su propio hilo, y un HashMap normal no es seguro en esa situación.
    private final ConcurrentHashMap<String, Closeable> streamsPorSesion = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession sesion) throws Exception {
        String ruta = sesion.getUri().getPath();
        // aqui lo que hacemos es de la ruta extraer un string que coga y la ultima barra leyendo de izquierda a derecha y le sumamos 1 a la posicion
        //para que quede solo la id del servidor
        Long id = Long.parseLong(ruta.substring(ruta.lastIndexOf('/') + 1));

        Servidor servidor = servicio.obtenerPorId(id);

        // Arrancamos el stream de logs de Docker
        Closeable stream = dockerClient.logContainerCmd(servidor.getIdContenedor())
                .withStdOut(true) // captura la consola normal de Minecraft
                .withStdErr(true) // captura también los errores
                .withFollowStream(true) //no para al llegar al final, se queda escuchando
                .withTail(50) //al conectarse envía las últimas 50 líneas para que la consola no aparezca vacía
                .exec(new ResultCallback.Adapter<Frame>() {
                    @Override
                    public void onNext(Frame frame) {
                        try {
                            if (sesion.isOpen()) {
                                // Cada línea que genera el servidor de Minecraft se envía al frontend por WebSocket
                                //elgetpayload es el contenido de la línea en bytes
                                //new String(...).trim() lo convierte a texto
                                String linea = new String(frame.getPayload()).trim();
                                // no enviamos líneas vacias
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
        //aqui es donde llamamos al hashmap
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
