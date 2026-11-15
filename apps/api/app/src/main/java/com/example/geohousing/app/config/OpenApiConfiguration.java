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

  /**
   * Public schema properties whose value can be the object itself or JSON {@code null}. The value
   * is the schema a non-null value points at.
   */
  private static final Map<String, Map<String, String>> OBJECT_OR_NULL_FIELDS =
      Map.of(
          "PropertySearchHitResponse", Map.of("address", "AddressSummaryView"),
          "PropertyResponse", Map.of("address", "AddressView"),
          "ReviewResponse", Map.of("content", "ContentView"));

  /**
   * Public schema properties that may be JSON {@code null}. A field belongs here only when the
   * DTO's {@code from()} mapping can actually yield {@code null}; a missing entry is what makes the
   * mobile client's narrow-only guard either correct or falsely strict.
   */
  private static final Map<String, Set<String>> NULLABLE_FIELDS =
      Map.of(
          "AddressSummaryView", Set.of("city", "district", "street", "building"),
          "AddressView", Set.of("city", "district", "street", "building"),
          "AliasView", Set.of("confidence"),
          "PropertyResponse",
              Set.of("latitude", "longitude", "parentPropertyId", "mergedIntoPropertyId"),
          "PropertySearchHitResponse", Set.of("distanceMeters"),
          "ReviewListResponse", Set.of("nextCursor"),
          "ReviewResponse", Set.of("residenceFrom", "residenceTo", "publishedAt"),
          "ContentView", Set.of("pros", "cons"),
          "RatingView", Set.of("value", "note"));

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
   * <p>A search hit has no address row when the property has never had one, every address part is
   * absent for a partial address, and a detail/review response can likewise omit an optional object
   * or value. The modules cannot depend on swagger annotations without crossing a module boundary
   * (AGENTS §3.5), so the API layer records the facts here, where the generated document is owned.
   * JSON Schema 2020-12 spells "this object or null" as an {@code anyOf} with a null arm; that is
   * the form openapi-typescript renders as {@code | null}.
   *
   * <p>Both the schema and every named property are required: a rename must fail here rather than
   * silently publish a document that no longer describes the HTTP response.
   */
  @Bean
  OpenApiCustomizer publicSchemaNullability() {
    return openApi -> {
      Map<String, Schema> schemas = componentSchemas(openApi);
      OBJECT_OR_NULL_FIELDS.forEach(
          (schemaName, fields) -> {
            Schema<?> schema = requireSchema(schemas, schemaName);
            fields.forEach(
                (field, target) -> {
                  requireProperty(schema, field, schemaName);
                  schema.addProperty(field, objectOrNull(target));
                });
          });
      NULLABLE_FIELDS.forEach(
          (schemaName, fields) -> {
            Schema<?> schema = requireSchema(schemas, schemaName);
            for (String field : fields) {
              markNullable(schema, field, schemaName);
            }
          });
    };
  }

  private static Schema<?> objectOrNull(String targetSchema) {
    return new Schema<>()
        .addAnyOfItem(new Schema<>().$ref("#/components/schemas/" + targetSchema))
        .addAnyOfItem(new Schema<>().types(new LinkedHashSet<>(List.of("null"))));
  }

  private static void markNullable(Schema<?> owner, String field, String ownerName) {
    Schema<?> property = requireProperty(owner, field, ownerName);
    Set<String> types = new LinkedHashSet<>();
    if (property.getTypes() != null) {
      types.addAll(property.getTypes());
    }
    if (property.getType() != null) {
      types.add(property.getType());
    }
    if (types.isEmpty()) {
      throw new IllegalStateException(
          "property '" + field + "' on schema '" + ownerName + "' has no declared type");
    }
    types.add("null");
    property.setTypes(types);
  }

  private static Map<String, Schema> componentSchemas(OpenAPI openApi) {
    if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
      throw new IllegalStateException(
          "the generated OpenAPI document is missing component schemas");
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
