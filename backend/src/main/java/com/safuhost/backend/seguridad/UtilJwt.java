package com.safuhost.backend.seguridad;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class UtilJwt {

    private static final String SECRET = "safuhost_clave_secreta_super_larga_que_no_debe_compartirse_jamas_2026";

    private final SecretKey clave = Keys.hmacShaKeyFor(SECRET.getBytes());

    private static final long DURACION_MS = 24 * 60 * 60 * 1000L;

    public String generarToken(String username) {
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + DURACION_MS))
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
