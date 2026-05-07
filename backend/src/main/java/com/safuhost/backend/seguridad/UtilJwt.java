package com.safuhost.backend.seguridad;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

// Esta clase encapsula toda la logica de JWT: crear tokens, validarlos y sacar el username de dentro.
// Un JWT es como un ticket de entrada firmado: contiene info del usuario + fecha de caducidad,
// y el servidor lo firma con una clave secreta para que nadie pueda falsificarlo.

// La usan:
//  - ServicioUsuario.login() para generar el token cuando el usuario se loguea correctamente
//  - FiltroJwt para validar el token de cada peticion entrante y extraer el username

// Un token JWT se ve asi: eyJhbGciOiJIUzI1NiIs... y tiene 3 partes separadas por puntos:
//  - header (algoritmo de firma)
//  - payload (los "claims": username, fecha emision, fecha caducidad)
//  - signature (firma con la clave secreta)
// El payload se puede LEER sin la clave (no esta cifrado, solo codificado en base64),
// pero NO se puede modificar sin invalidar la firma. Por eso nunca metemos info sensible ahi.

@Component
public class UtilJwt {

    // Clave secreta usada para firmar los tokens. Debe ser larga (mínimo 256 bits = 32 caracteres)
    // En un proyecto real iría en application.properties o en una variable de entorno.
    // NUNCA debe compartirse ni subirse a github. Si alguien la consigue puede crear tokens válidos para tu app.
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
        // Si el token está manipulado o caducado lanza una excepción automáticamente,
        // por eso el metodo de abajo (tokenValido) usa try/catch para detectarlo
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
