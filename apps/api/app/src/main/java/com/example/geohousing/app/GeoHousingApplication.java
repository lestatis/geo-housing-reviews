package com.example.geohousing.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.example.geohousing")
@EntityScan(basePackages = "com.example.geohousing.identity.infrastructure.persistence")
@EnableJpaRepositories(basePackages = "com.example.geohousing.identity.infrastructure.persistence")
public class GeoHousingApplication {

  public static void main(String[] args) {
    SpringApplication.run(GeoHousingApplication.class, args);
  }
}
