package com.example.acisapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Punto de entrada de la API de ACIS. Se inicia con: mvnw spring-boot:run */
@SpringBootApplication
public class AcisApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AcisApiApplication.class, args);
    }
}
