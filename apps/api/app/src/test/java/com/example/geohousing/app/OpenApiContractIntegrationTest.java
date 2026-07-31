package com.example.geohousing.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Pins the published contract to a file in the repository, because the admin web app generates its
 * types from that file rather than from a running server.
 *
 * <p>Without this the generated client would drift silently: the API would change, the checked-in
 * spec would not, and the frontend would keep compiling against a contract that no longer exists.
 * Here a contract change is a failing test with a one-command fix, which is the point — the diff
 * lands in the same commit as the change that caused it and a reviewer sees it.
 *
 * <p>Regenerate with {@code ./gradlew :app:test --tests '*OpenApiContractIntegrationTest'
 * -DupdateOpenApiSpec=true}.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiContractIntegrationTest {

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"));

  private static final String SPEC_PATH = "docs/api/openapi.json";

  @Autowired private MockMvc mockMvc;

  @Test
  void theCheckedInSpecIsWhatTheApplicationActuallyPublishes() throws Exception {
    String published = canonical(fetchSpec());
    Path spec = repositoryRoot().resolve(SPEC_PATH);

    if (Boolean.getBoolean("updateOpenApiSpec")) {
      Files.createDirectories(spec.getParent());
      Files.writeString(spec, published, StandardCharsets.UTF_8);
      return;
    }

    assertThat(spec)
        .as("%s is missing; regenerate it with -DupdateOpenApiSpec=true", SPEC_PATH)
        .exists();
    assertThat(Files.readString(spec, StandardCharsets.UTF_8))
        .as(
            "the API contract changed but %s was not regenerated, so apps/web's generated client is"
                + " stale; rerun this test with -DupdateOpenApiSpec=true and commit the diff",
            SPEC_PATH)
        .isEqualTo(published);
  }

  private String fetchSpec() throws Exception {
    return mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString(StandardCharsets.UTF_8);
  }

  /**
   * Sorts every object's keys and pretty-prints with a trailing newline. Springdoc builds the
   * document from hash-ordered maps, so byte-comparing its raw output would fail at random.
   */
  private static String canonical(String json) throws IOException {
    ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    Map<String, Object> document = mapper.readValue(json, new TypeReference<>() {});
    return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(document) + "\n";
  }

  /** Walks up from the test's working directory to the directory holding {@code .git}. */
  private static Path repositoryRoot() {
    Path candidate = Paths.get("").toAbsolutePath();
    while (candidate != null && !Files.exists(candidate.resolve(".git"))) {
      candidate = candidate.getParent();
    }
    if (candidate == null) {
      throw new IllegalStateException("no repository root above " + Paths.get("").toAbsolutePath());
    }
    return candidate;
  }
}
