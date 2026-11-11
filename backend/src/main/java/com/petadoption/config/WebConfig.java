package com.petadoption.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Override
    public void addViewControllers(@NonNull ViewControllerRegistry registry) {
        // Redirect root url directly to login page
        registry.addRedirectViewController("/", "/login.html");
    }

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        File parentDir = new File("../PetAdoptionSystem");
        File currentDir = new File("PetAdoptionSystem");

        if (parentDir.exists()) {
            registry.addResourceHandler("/**")
                    .addResourceLocations(parentDir.toURI().toString(), "classpath:/static/");
        } else if (currentDir.exists()) {
            registry.addResourceHandler("/**")
                    .addResourceLocations(currentDir.toURI().toString(), "classpath:/static/");
        } else {
            registry.addResourceHandler("/**")
                    .addResourceLocations("classpath:/static/");
        }
    }
}
