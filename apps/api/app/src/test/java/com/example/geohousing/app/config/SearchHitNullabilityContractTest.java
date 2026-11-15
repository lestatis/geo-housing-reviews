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
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pins the nullable address shape in the generated document.
 *
 * <p>The record signatures cannot express "this object or null", and the properties module may not
 * depend on swagger annotations, so {@link OpenApiConfiguration} adds the fact to the document. A
 * customizer that silently matches nothing after a rename would leave the mobile client believing
 * the backend never sends {@code null}; this test makes that a failure instead.
 */
@ExtendWith(SharedPostgis.ResetBeforeClass.class)
@SpringBootTest
@AutoConfigureMockMvc
class SearchHitNullabilityContractTest {

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    SharedPostgis.register(registry);
  }

  @Autowired private MockMvc mockMvc;

  @Test
  void marksTheSearchHitAddressAndEveryPartNullable() throws Exception {
    JsonNode schemas = publishedDocument().path("components").path("schemas");

    JsonNode address = schemas.path("PropertySearchHitResponse").path("properties").path("address");
    JsonNode anyOf = address.path("anyOf");
    assertThat(anyOf.isArray()).as("address.anyOf is a JSON Schema array").isTrue();
    assertThat(anyOf.size()).as("address.anyOf has the $ref and null arms").isEqualTo(2);
    assertThat(address.at("/anyOf/0/$ref").asText())
        .isEqualTo("#/components/schemas/AddressSummaryView");
    assertThat(address.at("/anyOf/1/type").asText()).isEqualTo("null");

    for (String part : List.of("city", "district", "street", "building")) {
      JsonNode type = schemas.path("AddressSummaryView").path("properties").path(part).path("type");
      assertThat(type.isArray()).as("%s.type is a JSON Schema type array", part).isTrue();
      assertThat(type.get(0).asText()).isEqualTo("string");
      assertThat(type.get(1).asText()).isEqualTo("null");
    }
  }

  @Test
  void failsFastWhenTheSearchHitSchemaIsMissing() {
    OpenAPI document = new OpenAPI().components(new Components());

    assertThatThrownBy(
            () -> new OpenApiConfiguration().searchHitAddressNullability().customise(document))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("PropertySearchHitResponse");
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
}
