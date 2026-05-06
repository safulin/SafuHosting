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
            String token = cabecera.substring(7); // quitamos "Bearer " (los primeros 7 caracteres)

            if (utilJwt.tokenValido(token)) {
                String username = utilJwt.extraerUsername(token);

                // 3. Le decimos a Spring Security que esta petición está autenticada como ese usuario
                // El segundo parámetro (credentials) lo dejamos null porque ya validamos con el token
                // El tercero (authorities) es la lista de roles, vacía porque no usamos roles
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(username, null, new ArrayList<>());

                // SecurityContextHolder es el almacén global de Spring para la sesión actual
                // A partir de aquí cualquier servicio puede preguntar quién es el usuario logueado
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        // 4. Pasamos el control al siguiente filtro de la cadena (o al controlador si no hay más)
        // Si no había token o era inválido, simplemente sigue sin autenticar
        // Spring Security comprobará después si la ruta requiere autenticación o no
        filterChain.doFilter(request, response);
    }
}
