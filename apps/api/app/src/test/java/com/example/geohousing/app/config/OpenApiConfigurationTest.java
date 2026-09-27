package com.example.geohousing.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OpenApiConfigurationTest {

  private static final String REF = "#/components/schemas/AddressSummaryView";

  @Test
  void aNullableReferenceBecomesTheReferencedSchemaOrNull() {
    Schema<?> property = customise(new JsonSchema().$ref(REF).types(Set.of("null")));

    assertThat(property.get$ref()).isNull();
    assertThat(property.getTypes()).isNull();
    assertThat(property.getOneOf()).hasSize(2);
    assertThat(property.getOneOf().get(0).get$ref()).isEqualTo(REF);
    assertThat(property.getOneOf().get(1).getTypes()).containsExactly("null");
  }

  @Test
  void aNonNullableReferenceIsLeftAlone() {
    Schema<?> original = new JsonSchema().$ref(REF);

    assertThat(customise(original)).isSameAs(original);
  }

  @Test
  void aNullableScalarIsLeftAlone() {
    Schema<?> original = new JsonSchema().types(Set.of("string", "null"));

    assertThat(customise(original)).isSameAs(original);
  }

  private static Schema<?> customise(Schema<?> property) {
    Schema<?> owner = new JsonSchema().addProperty("address", property);
    OpenAPI openApi = new OpenAPI().components(new Components().addSchemas("Hit", owner));

    new OpenApiConfiguration().nullableReferences().customise(openApi);

    return (Schema<?>)
        openApi.getComponents().getSchemas().get("Hit").getProperties().get("address");
  }
}
