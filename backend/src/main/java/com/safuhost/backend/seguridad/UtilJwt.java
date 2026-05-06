package com.safuhost.backend.seguridad;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class UtilJwt {

    // Clave secreta usada para firmar los tokens. Debe ser larga (mínimo 256 bits = 32 caracteres)
    // En un proyecto real iría en application.properties o en una variable de entorno
    // NUNCA debe compartirse ni subirse a github. Si alguien la consigue puede crear tokens válidos para tu app
    private static final String SECRET = "safuhost_clave_secreta_super_larga_que_no_debe_compartirse_jamas_2026";

    // Convertimos el String en una SecretKey que es lo que espera la libreria jjwt
    private final SecretKey clave = Keys.hmacShaKeyFor(SECRET.getBytes());

    // Duración del token: 24 horas en milisegundos
    private static final long DURACION_MS = 24 * 60 * 60 * 1000L;

    public String generarToken(String username) {
        // Construimos el token con:
        //  - subject: el nombre del usuario (lo identificamos por aquí)
        //  - issuedAt: fecha de emisión
        //  - expiration: fecha de caducidad
        //  - signWith: firma con nuestra clave secreta para que nadie pueda falsificarlo
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + DURACION_MS))
                .signWith(clave)
                .compact();
    }

    public String extraerUsername(String token) {
        // Validamos la firma con la clave y extraemos el subject (el username)
        // Si el token está manipulado o caducado lanza una excepción automáticamente
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
            // Token caducado, manipulado o malformado
            return false;
        }
    }
}
