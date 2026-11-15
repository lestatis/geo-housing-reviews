package com.example.geohousing.app;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.geohousing.app.support.SharedPostgis;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Confirms documentation is intentionally public while the generated contract tells consumers to
 * use the same bearer authentication that protects application endpoints.
 */
@ExtendWith(SharedPostgis.ResetBeforeClass.class)
@SpringBootTest
@AutoConfigureMockMvc
class SwaggerEndpointIntegrationTest {

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    SharedPostgis.register(registry);
  }

  @Autowired private MockMvc mockMvc;

  @Test
  void publishesOpenApiDocumentWithBearerAuthentication() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.openapi").exists())
        .andExpect(jsonPath("$.info.title").value("Geo Housing Reviews API"))
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
        .andExpect(jsonPath("$.security[0].bearerAuth").isArray());
  }

  @Test
  void publishesSwaggerUiWithoutAuthentication() throws Exception {
    mockMvc
        .perform(get("/swagger-ui/index.html"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
  }

  @Test
  void publishesTheYamlOpenApiDocumentWithoutAuthentication() throws Exception {
    // API_GUIDELINES advertises /v3/api-docs.yaml as public; it is a sibling path, so the
    // /v3/api-docs/** matcher does not cover it and it must be permitted explicitly.
    mockMvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk());
  }

  @Test
  void keepsApplicationEndpointsProtected() throws Exception {
    mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
  }
}
