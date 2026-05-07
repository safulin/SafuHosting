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

// Aqui montamos toda la seguridad de la app. Spring Security por defecto bloquea TODAS las rutas con un login
// generico de formulario (genera una contraseña aleatoria al arrancar y la imprime en consola). Eso no nos sirve
// porque queremos JWT, asique aqui sobrescribimos ese comportamiento.

// Este archivo decide:
//  - Que rutas son publicas (registro, login, websocket de la consola)
//  - Que rutas necesitan JWT valido
//  - Como se cifran las contraseñas (BCrypt)
//  - Que activa CORS (usando el bean de ConfiguracionCors)
//  - Que mete el FiltroJwt en la cadena de filtros para validar el token de cada peticion

@Configuration
public class ConfiguracionSeguridad {

    @Autowired
    private FiltroJwt filtroJwt;

    // Bean de PasswordEncoder con el algoritmo BCrypt
    // BCrypt cifra las contraseñas de forma irreversible:
    // de "miPassword123" sale algo como "$2a$10$N9qo8uLOickgx2ZMRZoMye..."
    // y ya no se puede volver atrás. Por eso al hacer login lo que se compara es el hash, no la contraseña en claro.
    // Este bean lo usa ServicioUsuario para cifrar al registrar y para comparar al hacer login.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CORS: usa el bean CorsConfigurationSource de ConfiguracionCors automaticamente
                .cors(Customizer.withDefaults())
                // Desactivamos CSRF porque usamos JWT en cabecera, no cookies de sesión.
                // CSRF protege contra ataques que reutilizan tu cookie de sesion, pero como no tenemos cookies, no aplica.
                .csrf(csrf -> csrf.disable())
                // Sin sesiones del lado del servidor: cada petición lleva su propio token y se valida sola.
                // STATELESS = Spring no guarda nada entre peticiones, no hay HttpSession, todo va en el JWT.
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Rutas públicas que no requieren login. Si esto no estuviera, no podrias ni registrarte
                        // porque para registrarte tendrias que estar... ya logueado.
                        .requestMatchers("/api/usuarios/registro", "/api/usuarios/login").permitAll()
                        // El WebSocket de la consola lo dejamos abierto de momento.
                        // En un proyecto real habría que validar el token también ahí (mandandolo como query param)
                        .requestMatchers("/ws/**").permitAll()
                        // Cualquier otra ruta requiere estar autenticado con JWT
                        .anyRequest().authenticated()
                )
                // Insertamos nuestro filtro JWT antes del filtro estándar de login por usuario+contraseña.
                // Asi cuando llega una peticion, primero pasa por FiltroJwt que mira la cabecera Authorization,
                // valida el token y mete al usuario en el SecurityContext. Despues los servicios pueden saber
                // quien es el usuario logueado llamando a SecurityContextHolder.
                .addFilterBefore(filtroJwt, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
