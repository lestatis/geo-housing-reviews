package com.example.geohousing.app.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadata and authentication contract for the generated public OpenAPI document. Springdoc derives
 * operations and schemas from the MVC controllers; this configuration records the API-wide
 * information that cannot be inferred from those signatures.
 */
@Configuration
public class OpenApiConfiguration {

  static final String BEARER_AUTH_SCHEME = "bearerAuth";

  @Bean
  OpenAPI geoHousingOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Geo Housing Reviews API")
                .description("API for the Georgian housing-review platform.")
                .version("v1"))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER_AUTH_SCHEME,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH_SCHEME));
  }
}
