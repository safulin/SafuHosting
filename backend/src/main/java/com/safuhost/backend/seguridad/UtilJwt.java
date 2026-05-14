package com.safuhost.backend.seguridad;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class UtilJwt {

    @Value("${safuhost.jwt.secret}")
    private String secret;

    @Value("${safuhost.jwt.duracion-ms}")
    private long duracionMs;

    private SecretKey clave;

    @PostConstruct
    public void init() {
        this.clave = Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generarToken(String username) {
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + duracionMs))
                .signWith(clave)
                .compact();
    }

    public String extraerUsername(String token) {
        return Jwts.parser()
                .verifyWith(clave)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public boolean tokenValido(String token) {
        try {
            extraerUsername(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
