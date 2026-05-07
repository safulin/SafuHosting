package com.safuhost.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Punto de entrada de toda la aplicacion. Es el primer archivo que se ejecuta cuando arrancamos el backend
// con mvn spring-boot:run o desde el boton de play de IntelliJ.

// @SpringBootApplication es una anotacion "todo en uno" que activa tres cosas a la vez:
//  - @Configuration: dice que esta clase puede declarar beans
//  - @EnableAutoConfiguration: arranca toda la magia de Spring (Tomcat, JPA, Security, etc.)
//  - @ComponentScan: busca por todo el paquete com.safuhost.backend las clases marcadas como
//    @Service, @Component, @RestController, @Repository, @Configuration, etc. y las registra automaticamente.
//    Por eso no hace falta registrar a mano cada servicio o controlador.

@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		// SpringApplication.run levanta Tomcat embebido en el puerto 8080 (configurado en application.properties),
		// inicializa la conexion con SQLite, escanea todos los componentes y deja el servidor escuchando peticiones HTTP.
		SpringApplication.run(BackendApplication.class, args);
	}

}
