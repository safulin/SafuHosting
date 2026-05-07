package com.safuhost.backend.configuracion;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Aqui creamos UNA sola conexion con Docker para toda la aplicacion. Spring se queda con este DockerClient
// y se lo pasa con @Autowired a quien lo pida (ServicioServidor, ServicioDocker, ConsolaWebSocketHandler, etc.)
// Asi no creamos un cliente nuevo cada vez que queremos hablar con Docker.

@Configuration // esta anotacion sirve para que cuando spring inicie, lea este archivo entero y lo carge en memoria, sin la anotacion java solo
//veria una clase mas y no la leeria
public class ConfiguracionDocker {

    // El @Bean le dice a Spring "guarda lo que devuelve este metodo y dasselo a quien lo pida con @Autowired"
    @Bean
    public DockerClient clienteDocker() {
        // 1. Configuramos a que Docker queremos hablar. tcp://localhost:2375 es el "telefono" donde escucha Docker Desktop
        // cuando tienes activado en sus ajustes "Expose daemon on tcp://localhost:2375 without TLS".
        // Sin TLS porque estamos en local, en produccion habria que usar TLS por seguridad.
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost("tcp://localhost:2375")
                .build();

        // 2. Creamos el cliente HTTP que se conecta a ese puerto. ApacheDockerHttpClient es el "cartero" que lleva
        // las peticiones (crear contenedor, parar, leer logs...) hasta Docker
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .build();

        // 3. Juntamos config + httpClient en un DockerClient que es la API de alto nivel que usa el resto de la app
        return DockerClientImpl.getInstance(config, httpClient);
    }
}
