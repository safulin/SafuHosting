package com.safuhost.backend.configuracion;

import com.safuhost.backend.seguridad.FiltroJwt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class ConfiguracionSeguridad {

    @Autowired
    private FiltroJwt filtroJwt;

    // Bean de PasswordEncoder con el algoritmo BCrypt
    // BCrypt cifra las contraseñas de forma irreversible:
    // de "miPassword123" sale algo como "$2a$10$N9qo8uLOickgx2ZMRZoMye..."
    // y ya no se puede volver atrás. Por eso al hacer login lo que se compara es el hash, no la contraseña en claro
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CORS: usa el bean CorsConfigurationSource de ConfiguracionCors
                .cors(Customizer.withDefaults())
                // Desactivamos CSRF porque usamos JWT en cabecera, no cookies de sesión
                .csrf(csrf -> csrf.disable())
                // Sin sesiones del lado del servidor: cada petición lleva su propio token y se valida sola
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Rutas públicas que no requieren login
                        .requestMatchers("/api/usuarios/registro", "/api/usuarios/login").permitAll()
                        // El WebSocket de la consola lo dejamos abierto de momento
                        // En un proyecto real habría que validar el token también ahí
                        .requestMatchers("/ws/**").permitAll()
                        // Cualquier otra ruta requiere estar autenticado con JWT
                        .anyRequest().authenticated()
                )
                // Insertamos nuestro filtro JWT antes del filtro estándar de login por usuario+contraseña
                // Así interceptamos el token en la cabecera antes de que Spring intente otra autenticación
                .addFilterBefore(filtroJwt, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
