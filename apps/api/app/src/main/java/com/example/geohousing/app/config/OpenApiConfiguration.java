package com.example.geohousing.app.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
  private static final String SEARCH_HIT_SCHEMA = "PropertySearchHitResponse";
  private static final String ADDRESS_SUMMARY_SCHEMA = "AddressSummaryView";
  private static final List<String> ADDRESS_PARTS =
      List.of("city", "district", "street", "building");

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
   * Declares nullability that the record signatures cannot express.
   *
   * <p>A search hit has no address row when the property has never had one, and every address part
   * is absent for a partial address. The properties module cannot depend on swagger annotations
   * without crossing a module boundary (AGENTS §3.5), so the API layer records the fact here, where
   * the generated document is owned. JSON Schema 2020-12 spells "this object or null" as an {@code
   * anyOf} with a null arm; that is the form openapi-typescript renders as {@code | null}.
   *
   * <p>Both the schema and every named property are required: a rename must fail here rather than
   * silently publish a document that no longer describes the HTTP response.
   */
  @Bean
  OpenApiCustomizer searchHitAddressNullability() {
    return openApi -> {
      Map<String, Schema> schemas = componentSchemas(openApi);
      Schema searchHit = requireSchema(schemas, SEARCH_HIT_SCHEMA);
      requireProperty(searchHit, "address", SEARCH_HIT_SCHEMA);
      searchHit.addProperty(
          "address",
          new Schema<>()
              .addAnyOfItem(new Schema<>().$ref("#/components/schemas/" + ADDRESS_SUMMARY_SCHEMA))
              .addAnyOfItem(new Schema<>().types(new LinkedHashSet<>(List.of("null")))));

      Schema addressSummary = requireSchema(schemas, ADDRESS_SUMMARY_SCHEMA);
      for (String part : ADDRESS_PARTS) {
        requireProperty(addressSummary, part, ADDRESS_SUMMARY_SCHEMA)
            .setTypes(new LinkedHashSet<>(List.of("string", "null")));
      }
    };
  }

  private static Map<String, Schema> componentSchemas(OpenAPI openApi) {
    if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
      throw new IllegalStateException(
          "the generated OpenAPI document is missing component schema '" + SEARCH_HIT_SCHEMA + "'");
    }
    return openApi.getComponents().getSchemas();
  }

  private static Schema<?> requireSchema(Map<String, Schema> schemas, String name) {
    Schema<?> schema = schemas.get(name);
    if (schema == null) {
      throw new IllegalStateException(
          "the generated OpenAPI document is missing component schema '" + name + "'");
    }
    return schema;
  }

  private static Schema<?> requireProperty(Schema<?> owner, String property, String ownerName) {
    Map<String, Schema> properties = owner.getProperties();
    Schema<?> propertySchema = properties == null ? null : properties.get(property);
    if (propertySchema == null) {
      throw new IllegalStateException(
          "the generated OpenAPI document is missing property '"
              + property
              + "' on schema '"
              + ownerName
              + "'");
    }
    return propertySchema;
  }
}
