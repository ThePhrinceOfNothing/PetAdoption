package com.petadoption;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PetAdoptionApplication {

    public static void main(String[] args) {
        SpringApplication.run(PetAdoptionApplication.class, args);
    }
}
