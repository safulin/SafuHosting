package com.safuhost.backend.configuracion;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

// CORS = Cross-Origin Resource Sharing. Es una norma de seguridad del navegador:
// si el frontend (en localhost:5173) intenta llamar al backend (localhost:8080) y son origenes distintos,
// el navegador lo bloquea SALVO que el backend responda con la cabecera Access-Control-Allow-Origin diciendo
// "te dejo entrar". Aqui creamos la regla que define quien puede entrar.

// En desarrollo permitimos cualquier origen porque el frontend pasa por el proxy de Vite,
// pero igualmente esto cubre llamadas directas (Postman, otro front, etc.).

// Este bean lo USA ConfiguracionSeguridad cuando hace .cors(Customizer.withDefaults()),
// Spring Security busca un CorsConfigurationSource registrado y aplica esta config a todas las peticiones.

@Configuration
public class ConfiguracionCors {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // setAllowedOriginPatterns acepta comodines tipo "*", a diferencia de setAllowedOrigins que es estricto
        config.setAllowedOriginPatterns(List.of("*"));
        // Metodos HTTP que el frontend puede usar. OPTIONS es CRITICO porque el navegador lo manda como
        // "preflight" (pregunta previa) antes de cualquier POST/PUT/DELETE para comprobar que el backend acepta CORS
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"));
        config.setAllowedHeaders(List.of("*"));
        // false porque no usamos cookies de sesion, todo va con JWT en la cabecera Authorization
        config.setAllowCredentials(false);

        // Esta source dice "aplica esta config a TODAS las rutas (/**)"
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
