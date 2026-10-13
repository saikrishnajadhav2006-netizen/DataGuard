package com.dataguard.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // Allow the React frontend to communicate with the Spring Boot backend
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    public WebConfig(@Value("${app.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") String configuredOrigins) {
        // Keep the known production Vercel origin available even if a Render environment
        // variable was left with local-only origins. Do not use wildcard origins with JWT.
        this.allowedOrigins = java.util.stream.Stream.concat(
                        Arrays.stream(configuredOrigins.split(",")),
                        java.util.stream.Stream.of("https://data-guard-ecru.vercel.app"))
                .map(String::trim)
                .map(origin -> origin.endsWith("/") ? origin.substring(0, origin.length() - 1) : origin)
                .filter(origin -> !origin.isEmpty())
                .distinct()
                .toArray(String[]::new);
    }
}
