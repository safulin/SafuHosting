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

// Handler que gestiona las conexiones WebSocket en la URL /ws/consola/{id}.
// Cuando el frontend (Consola.vue) abre el WebSocket, llega aqui afterConnectionEstablished:
//  1. Sacamos el id del servidor de la URL
//  2. Pedimos a Docker el stream de logs de ese contenedor (sigue en tiempo real, como tail -f)
//  3. Cada linea nueva que escupe Minecraft se la mandamos al frontend por el WebSocket
//  4. Cuando el usuario cierra la consola, paramos el stream para liberar recursos en Docker

// Lo registra ConfiguracionWebSocket que mapea /ws/consola/* a este handler.
// Para ENVIAR comandos (no recibir logs) el frontend hace un POST normal a /api/servidores/{id}/consola/comando,
// que va por el camino normal HTTP, no por el WebSocket.

@Component
public class ConsolaWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private DockerClient dockerClient;

    // Usamos el repositorio directamente porque los WebSockets corren en hilos de Tomcat
    // que no tienen SecurityContext (el JWT solo se procesa en hilos HTTP).
    // ServicioServidor.obtenerPorId() llama a verificarPropiedad() que lee el SecurityContext y explota con NPE.
    @Autowired
    private RepositorioServidor repositorio;

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

        Servidor servidor = repositorio.findById(id)
                .orElseThrow(() -> new RuntimeException("No existe ningún servidor con el id: " + id));

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
