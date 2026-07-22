package com.example.geohousing.app.properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Drives {@code /api/properties} end to end through the real security chain (stub {@link
 * JwtDecoder}, as in the identity endpoint tests), including the duplicate-candidates 409 that
 * makes chunk 5's detection visible to clients.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(PropertyEndpointIntegrationTest.StubJwtDecoderConfig.class)
class PropertyEndpointIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"));

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  @TestConfiguration
  static class StubJwtDecoderConfig {

    @Bean
    JwtDecoder jwtDecoder() {
      return token ->
          Jwt.withTokenValue(token)
              .header("alg", "none")
              .subject(token)
              .issuedAt(Instant.parse("2026-07-20T00:00:00Z"))
              .expiresAt(Instant.parse("2999-01-01T00:00:00Z"))
              .build();
    }
  }

  @Test
  void anonymousRequestsAreUnauthorized() throws Exception {
    mockMvc.perform(get("/api/properties")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            post("/api/properties")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("Anon Tower", false)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void createsADraftAndRecordsTheAuthenticatedCallerAsCreator() throws Exception {
    String bearer = bearer("subject-creator");
    String accountId = accountIdOf(bearer);
    String name = "Created Tower " + UUID.randomUUID();

    String body =
        mockMvc
            .perform(
                post("/api/properties")
                    .header("Authorization", bearer)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody(name, false)))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", Matchers.startsWith("/api/properties/")))
            .andExpect(jsonPath("$.status").value("DRAFT"))
            .andExpect(jsonPath("$.canonicalName").value(name))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // createdBy is intentionally absent from the response; assert it was persisted correctly.
    assertThat(body).doesNotContain("createdBy");
    String propertyId = JsonPath.read(body, "$.propertyId");
    String storedCreator =
        jdbcTemplate.queryForObject(
            "select created_by::text from properties.property where id = ?",
            String.class,
            UUID.fromString(propertyId));
    assertThat(storedCreator).isEqualTo(accountId);
  }

  @Test
  void aSecondCreateWithTheSameNameReturnsDuplicateCandidatesThenSucceedsWhenAllowed()
      throws Exception {
    String bearer = bearer("subject-dup");
    String name = "Duplicate Tower " + UUID.randomUUID();

    String firstBody =
        mockMvc
            .perform(
                post("/api/properties")
                    .header("Authorization", bearer)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody(name, false)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String firstId = JsonPath.read(firstBody, "$.propertyId");

    mockMvc
        .perform(
            post("/api/properties")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody(name, false)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PROPERTY_DUPLICATE_CANDIDATES"))
        .andExpect(jsonPath("$.candidates[*].propertyId").value(Matchers.hasItem(firstId)));

    mockMvc
        .perform(
            post("/api/properties")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody(name, true)))
        .andExpect(status().isCreated());
  }

  @Test
  void rejectsABlankNameWithThePropertiesProblemCode() throws Exception {
    mockMvc
        .perform(
            post("/api/properties")
                .header("Authorization", bearer("subject-invalid"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("   ", false)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  void getsAPropertyByIdAndReportsUnknownAndMalformedIds() throws Exception {
    String bearer = bearer("subject-reader");
    String name = "Readable Tower " + UUID.randomUUID();
    String created =
        mockMvc
            .perform(
                post("/api/properties")
                    .header("Authorization", bearer)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createBody(name, false)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String propertyId = JsonPath.read(created, "$.propertyId");

    mockMvc
        .perform(get("/api/properties/" + propertyId).header("Authorization", bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.canonicalName").value(name))
        .andExpect(jsonPath("$.type").value("BUILDING"));

    mockMvc
        .perform(get("/api/properties/" + UUID.randomUUID()).header("Authorization", bearer))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PROPERTY_NOT_FOUND"));

    mockMvc
        .perform(get("/api/properties/not-a-uuid").header("Authorization", bearer))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  void listsRecentPropertiesAndRespectsTheLimit() throws Exception {
    String bearer = bearer("subject-lister");
    mockMvc
        .perform(
            post("/api/properties")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody("Listed Tower " + UUID.randomUUID(), false)))
        .andExpect(status().isCreated());

    mockMvc
        .perform(get("/api/properties").header("Authorization", bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isArray())
        .andExpect(jsonPath("$.items.length()", Matchers.greaterThan(0)));

    mockMvc
        .perform(get("/api/properties").param("limit", "1").header("Authorization", bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1));
  }

  /** The identity advice is now package-scoped; its own endpoints must still map their errors. */
  @Test
  void identityEndpointsStillUseTheirOwnErrorMapping() throws Exception {
    String bearer = bearer("subject-identity-regression");
    mockMvc.perform(get("/api/me").header("Authorization", bearer)).andExpect(status().isOk());

    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                    "/api/me/profile")
                .header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"pseudonym\":\"bad!name\",\"avatarUrl\":null,\"locale\":\"en\",\"version\":0}"))
        .andExpect(status().is(422))
        .andExpect(jsonPath("$.code").value("INVALID_PSEUDONYM"));
  }

  private static String bearer(String subject) {
    return "Bearer " + subject;
  }

  private String accountIdOf(String bearer) throws Exception {
    String body =
        mockMvc
            .perform(get("/api/me").header("Authorization", bearer))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.accountId");
  }

  private static String createBody(String canonicalName, boolean allowDuplicate) {
    return "{\"type\":\"BUILDING\",\"canonicalName\":\""
        + canonicalName
        + "\",\"allowDuplicate\":"
        + allowDuplicate
        + "}";
  }
}
