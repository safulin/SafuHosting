package com.safuhost.backend.configuracion;

import com.safuhost.backend.websocket.ConsolaWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

//web socket es una manera de no usar http porque seria muy poco eficiente entonces usamos esta tecnologia que abre una conexion donde ni el frontend ni el backend tienen que preguntar para
//enviar una peticion

@Configuration // esta anotacion sirve para que cuando spring inicie, lea este archivo entero y lo carge en memoria, sin la anotacion java solo
//veria una clase mas y no la leeria


@EnableWebSocket // activa el soporte de WebSockets en Spring
public class ConfiguracionWebSocket implements WebSocketConfigurer {

    @Autowired
    private ConsolaWebSocketHandler consolaHandler;

    @Override // lo pongo para que spring no crea que el metodo lo estoy creando yo, asi si cometo algun fallo spring dira ese metodo no exsiste en websocketconfigurer
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Registramos el handler en la URL /ws/consola/*
        // El * permite que funcione con cualquier id: /ws/consola/1, /ws/consola/2, esto es para evitar el CORS
        registry.addHandler(consolaHandler, "/ws/consola/*")
                .setAllowedOrigins("*"); // permite conexiones desde el frontend
    }
}
