package com.example.geohousing.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.geohousing.app.support.SharedPostgis;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pins the nullable shape of every public response schema in the generated document.
 *
 * <p>The record signatures cannot express "this object or null", and the domain modules may not
 * depend on swagger annotations, so {@link OpenApiConfiguration} adds the facts to the document. A
 * customizer that silently matches nothing after a rename would leave the mobile client believing
 * the backend never sends {@code null}; this test makes that a failure instead. The table is also
 * asserted in the negative: a field not listed here must not claim to be nullable.
 */
@ExtendWith(SharedPostgis.ResetBeforeClass.class)
@SpringBootTest
@AutoConfigureMockMvc
class PublicNullabilityContractTest {

  private static final List<String> PUBLIC_SCHEMAS =
      List.of(
          "AddressSummaryView",
          "AddressView",
          "AliasView",
          "ContentView",
          "PropertyResponse",
          "PropertySearchHitResponse",
          "RatingView",
          "ReviewListResponse",
          "ReviewResponse");

  private static final List<NullableField> NULLABLE_FIELDS =
      List.of(
          new NullableField("AddressSummaryView", "city", "string"),
          new NullableField("AddressSummaryView", "district", "string"),
          new NullableField("AddressSummaryView", "street", "string"),
          new NullableField("AddressSummaryView", "building", "string"),
          new NullableField("AddressView", "city", "string"),
          new NullableField("AddressView", "district", "string"),
          new NullableField("AddressView", "street", "string"),
          new NullableField("AddressView", "building", "string"),
          new NullableField("AliasView", "confidence", "number"),
          new NullableField("PropertyResponse", "latitude", "number"),
          new NullableField("PropertyResponse", "longitude", "number"),
          new NullableField("PropertyResponse", "parentPropertyId", "string"),
          new NullableField("PropertyResponse", "mergedIntoPropertyId", "string"),
          new NullableField("PropertySearchHitResponse", "distanceMeters", "number"),
          new NullableField("ReviewListResponse", "nextCursor", "string"),
          new NullableField("ReviewResponse", "residenceFrom", "string"),
          new NullableField("ReviewResponse", "residenceTo", "string"),
          new NullableField("ReviewResponse", "publishedAt", "string"),
          new NullableField("ContentView", "pros", "string"),
          new NullableField("ContentView", "cons", "string"),
          new NullableField("RatingView", "value", "integer"),
          new NullableField("RatingView", "note", "string"));

  private static final List<ObjectOrNullField> OBJECT_OR_NULL_FIELDS =
      List.of(
          new ObjectOrNullField("PropertySearchHitResponse", "address", "AddressSummaryView"),
          new ObjectOrNullField("PropertyResponse", "address", "AddressView"),
          new ObjectOrNullField("ReviewResponse", "content", "ContentView"));

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    SharedPostgis.register(registry);
  }

  @Autowired private MockMvc mockMvc;

  @Test
  void publicSchemasDeclareExactlyTheFieldsTheirMappingsCanLeaveNull() throws Exception {
    JsonNode schemas = publishedDocument().path("components").path("schemas");

    for (NullableField expectation : NULLABLE_FIELDS) {
      JsonNode property = property(schemas, expectation.schema(), expectation.field());
      assertThat(types(property))
          .as("%s.%s is %s-or-null", expectation.schema(), expectation.field(), expectation.type())
          .containsExactlyInAnyOrder(expectation.type(), "null");
    }

    for (ObjectOrNullField expectation : OBJECT_OR_NULL_FIELDS) {
      JsonNode property = property(schemas, expectation.schema(), expectation.field());
      JsonNode anyOf = property.path("anyOf");
      assertThat(anyOf.isArray())
          .as("%s.%s is an object-or-null anyOf", expectation.schema(), expectation.field())
          .isTrue();
      assertThat(anyOf.size())
          .as("%s.%s has the $ref and null arms", expectation.schema(), expectation.field())
          .isEqualTo(2);
      assertThat(anyOf.at("/0/$ref").asText())
          .isEqualTo("#/components/schemas/" + expectation.targetSchema());
      assertThat(anyOf.at("/1/type").asText()).isEqualTo("null");
    }

    for (String schema : PUBLIC_SCHEMAS) {
      for (Map.Entry<String, JsonNode> entry : fields(schemas, schema).entrySet()) {
        String field = entry.getKey();
        if (isDeclaredNullable(schema, field)) {
          continue;
        }
        JsonNode property = entry.getValue();
        assertThat(types(property))
            .as("%s.%s is not accidentally nullable", schema, field)
            .doesNotContain("null");
        assertThat(property.has("anyOf"))
            .as("%s.%s is not accidentally object-or-null", schema, field)
            .isFalse();
      }
    }
  }

  @Test
  void failsFastWhenASchemaIsMissing() {
    OpenAPI document = new OpenAPI().components(new Components());

    assertThatThrownBy(
            () -> new OpenApiConfiguration().publicSchemaNullability().customise(document))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("missing component schemas");
  }

  @Test
  void failsFastWhenAPropertyIsMissing() {
    Components components = new Components();
    PUBLIC_SCHEMAS.forEach(schema -> components.addSchemas(schema, new Schema<>()));
    OpenAPI document = new OpenAPI().components(components);

    assertThatThrownBy(
            () -> new OpenApiConfiguration().publicSchemaNullability().customise(document))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("missing property");
  }

  private static JsonNode property(JsonNode schemas, String schema, String field) {
    JsonNode property = fields(schemas, schema).get(field);
    assertThat(property).as("%s.%s exists", schema, field).isNotNull();
    return property;
  }

  private static Map<String, JsonNode> fields(JsonNode schemas, String schema) {
    JsonNode properties = schemas.path(schema).path("properties");
    assertThat(properties.isObject()).as("%s.properties is an object", schema).isTrue();
    Map<String, JsonNode> fields = new HashMap<>();
    properties.fields().forEachRemaining(entry -> fields.put(entry.getKey(), entry.getValue()));
    return fields;
  }

  private static List<String> types(JsonNode property) {
    JsonNode type = property.path("type");
    List<String> types = new ArrayList<>();
    if (type.isArray()) {
      type.forEach(node -> types.add(node.asText()));
    } else if (type.isTextual()) {
      types.add(type.asText());
    }
    return types;
  }

  private static boolean isDeclaredNullable(String schema, String field) {
    return NULLABLE_FIELDS.stream()
            .anyMatch(
                expectation ->
                    expectation.schema().equals(schema) && expectation.field().equals(field))
        || OBJECT_OR_NULL_FIELDS.stream()
            .anyMatch(
                expectation ->
                    expectation.schema().equals(schema) && expectation.field().equals(field));
  }

  private JsonNode publishedDocument() throws Exception {
    String body =
        mockMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    return new ObjectMapper().readTree(body);
  }

  private record NullableField(String schema, String field, String type) {}

  private record ObjectOrNullField(String schema, String field, String targetSchema) {}
}
