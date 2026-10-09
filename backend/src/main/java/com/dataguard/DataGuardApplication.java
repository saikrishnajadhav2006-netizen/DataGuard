package com.dataguard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DataGuardApplication {
    public static void main(String[] args) {
        SpringApplication.run(DataGuardApplication.class, args);
    }
}
