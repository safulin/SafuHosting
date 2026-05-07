package com.safuhost.backend.seguridad;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

// Este filtro se ejecuta ANTES de que cualquier peticion llegue al controlador. Su trabajo es:
//  1. Mirar la cabecera Authorization de la peticion
//  2. Si trae un token JWT valido, sacar el username y meterlo en el SecurityContext de Spring
//  3. Pasar el control al siguiente filtro (o al controlador si ya no hay mas)

// Lo registra ConfiguracionSeguridad.securityFilterChain() con .addFilterBefore(filtroJwt, ...).
// Una vez el username esta en el SecurityContext, los servicios pueden saber quien es el usuario logueado
// llamando a ServicioUsuario.getUsuarioActual() (que internamente lee del SecurityContext).

// El usuario llega aqui porque en api.js del frontend tenemos un interceptor que mete
// "Authorization: Bearer <token>" en cada peticion, sacando el token de localStorage.

@Component
public class FiltroJwt extends OncePerRequestFilter {

    // OncePerRequestFilter garantiza que el filtro se ejecuta UNA sola vez por petición
    // (sin esto, podría ejecutarse varias veces al haber forwards/redirects internos)

    @Autowired
    private UtilJwt utilJwt;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {

        // 1. Sacamos la cabecera Authorization de la petición
        // El formato estándar es: "Authorization: Bearer eyJhbGciOiJIUzI1NiIs..."
        String cabecera = request.getHeader("Authorization");

        // 2. Si la cabecera existe y empieza por "Bearer ", procesamos el token
        if (cabecera != null && cabecera.startsWith("Bearer ")) {
            String token = cabecera.substring(7); // quitamos "Bearer " (los primeros 7 caracteres incluyendo el espacio)

            if (utilJwt.tokenValido(token)) {
                String username = utilJwt.extraerUsername(token);

                // 3. Le decimos a Spring Security que esta petición está autenticada como ese usuario
                // El segundo parámetro (credentials) lo dejamos null porque ya validamos con el token,
                // no necesitamos guardar la contraseña.
                // El tercero (authorities) es la lista de roles, vacía porque no usamos roles en la app.
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(username, null, new ArrayList<>());

                // SecurityContextHolder es el almacén global de Spring para la sesión actual.
                // A partir de aquí cualquier servicio puede preguntar quién es el usuario logueado
                // llamando a SecurityContextHolder.getContext().getAuthentication().getName()
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        // 4. Pasamos el control al siguiente filtro de la cadena (o al controlador si no hay más)
        // Si no había token o era inválido, simplemente sigue sin autenticar.
        // Spring Security comprobará después si la ruta requiere autenticación o no
        // (eso lo decide ConfiguracionSeguridad con .requestMatchers(...).permitAll()/authenticated())
        filterChain.doFilter(request, response);
    }
}
