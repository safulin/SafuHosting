package com.safuhost.backend.configuracion;

import com.safuhost.backend.websocket.ConsolaWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket // activa el soporte de WebSockets en Spring
public class ConfiguracionWebSocket implements WebSocketConfigurer {

    @Autowired
    private ConsolaWebSocketHandler consolaHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Registramos el handler en la URL /ws/consola/*
        // El * permite que funcione con cualquier id: /ws/consola/1, /ws/consola/2...
        registry.addHandler(consolaHandler, "/ws/consola/*")
                .setAllowedOrigins("*"); // permite conexiones desde el frontend
    }
}
