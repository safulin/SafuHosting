package com.safuhost.backend.websocket;

import com.safuhost.backend.seguridad.UtilJwt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
public class InterceptorHandshakeJwt implements HandshakeInterceptor {

    @Autowired
    private UtilJwt utilJwt;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler handler, Map<String, Object> atributos) {
        String query = request.getURI().getQuery();
        if (query == null) return false;

        String token = null;
        for (String parte : query.split("&")) {
            if (parte.startsWith("token=")) {
                token = parte.substring("token=".length());
                break;
            }
        }

        if (token == null || !utilJwt.tokenValido(token)) return false;

        atributos.put("username", utilJwt.extraerUsername(token));
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler handler, Exception exception) {
    }
}
