package com.safuhost.backend.configuracion;

import com.safuhost.backend.websocket.ConsolaWebSocketHandler;
import com.safuhost.backend.websocket.InterceptorHandshakeJwt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class ConfiguracionWebSocket implements WebSocketConfigurer {

    @Autowired
    private ConsolaWebSocketHandler consolaHandler;

    @Autowired
    private InterceptorHandshakeJwt interceptorJwt;

    @Value("${safuhost.frontend.origen}")
    private String origenFrontend;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(consolaHandler, "/ws/consola/*")
                .addInterceptors(interceptorJwt)
                .setAllowedOrigins(origenFrontend);
    }
}
