package com.example.geohousing.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.example.geohousing")
public class GeoHousingApplication {

  public static void main(String[] args) {
    SpringApplication.run(GeoHousingApplication.class, args);
  }
}
