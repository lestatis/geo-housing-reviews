package com.example.geohousing.app.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
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

  /**
   * Makes a nullable reference to another schema mean "that schema or null".
   *
   * <p>For {@code @Schema(nullable = true)} on a property whose type is another component,
   * swagger-core emits {@code {"$ref": ..., "type": "null"}}. Under OpenAPI 3.1 the sibling applies
   * together with the reference, so only {@code null} would be valid — the opposite of the intent.
   * This rewrites exactly that shape to {@code oneOf: [{"$ref": ...}, {"type": "null"}]} and leaves
   * every other property alone.
   */
  @Bean
  OpenApiCustomizer nullableReferences() {
    return openApi -> {
      if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
        return;
      }
      for (Schema<?> schema : openApi.getComponents().getSchemas().values()) {
        @SuppressWarnings("rawtypes")
        Map<String, Schema> properties = schema.getProperties();
        if (properties != null) {
          properties.replaceAll(
              (name, property) ->
                  isNullableReference(property) ? nullableReference(property) : property);
        }
      }
    };
  }

  private static boolean isNullableReference(Schema<?> property) {
    return property.get$ref() != null
        && property.getTypes() != null
        && property.getTypes().contains("null");
  }

  private static Schema<?> nullableReference(Schema<?> property) {
    return new JsonSchema()
        .oneOf(
            List.of(
                new JsonSchema().$ref(property.get$ref()), new JsonSchema().types(Set.of("null"))));
  }
}
