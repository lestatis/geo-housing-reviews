package com.example.geohousing.app.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Exercises the Tier 2 HTTP boundary with the real security chain, Postgres metadata, and MinIO
 * bytes. Fixtures are unmistakably synthetic and no test ever logs the document body.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Import(VerificationEndpointIntegrationTest.StubJwtDecoderConfig.class)
class EvidenceEndpointIntegrationTest {

  private static final String BUCKET = "verification-evidence";
  private static final byte[] SYNTHETIC =
      "SYNTHETIC-TEST-EVIDENCE-NOT-A-REAL-DOCUMENT".getBytes(StandardCharsets.UTF_8);

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"));

  @Container
  static final MinIOContainer MINIO =
      new MinIOContainer("minio/minio:RELEASE.2025-04-08T15-41-24Z")
          .withEnv("MINIO_KMS_SECRET_KEY", "key1:AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=")
          .withCreateContainerCmdModifier(
              command ->
                  command.withEntrypoint(
                      "/bin/sh",
                      "-c",
                      "mkdir -p /data/"
                          + BUCKET
                          + " && exec /usr/bin/docker-entrypoint.sh minio server /data --console-address :9001"));

  @DynamicPropertySource
  static void evidenceStorage(DynamicPropertyRegistry registry) {
    registry.add("verification.evidence.storage.endpoint", MINIO::getS3URL);
    registry.add("verification.evidence.storage.bucket", () -> BUCKET);
    registry.add("verification.evidence.storage.access-key-id", MINIO::getUserName);
    registry.add("verification.evidence.storage.secret-access-key", MINIO::getPassword);
    registry.add("verification.evidence.storage.path-style-access", () -> true);
    registry.add("verification.evidence.storage.max-upload-bytes", () -> 4096);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void ownerUploadsAndModeratorListsAndReadsWithAnAuditTrail() throws Exception {
    String owner = bearer("evidence-owner");
    String admin = adminBearer("evidence-admin");
    String caseId = openDocumentCase(owner);
    String evidenceId = upload(owner, caseId);

    mockMvc
        .perform(
            get("/api/admin/verifications/" + caseId + "/evidence").header("Authorization", admin))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].evidenceId").value(evidenceId))
        .andExpect(jsonPath("$.items[0].contentType").value(MediaType.APPLICATION_PDF_VALUE))
        .andExpect(jsonPath("$.items[0].sizeBytes").value(SYNTHETIC.length));

    mockMvc
        .perform(
            get("/api/admin/verifications/" + caseId + "/evidence/" + evidenceId)
                .header("Authorization", admin))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(
            header()
                .string("Content-Disposition", "attachment; filename=\"verification-evidence\""))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(content().contentType(MediaType.APPLICATION_PDF))
        .andExpect(content().bytes(SYNTHETIC));

    Integer reads =
        jdbcTemplate.queryForObject(
            "select count(*) from verification.verification_evidence_access_event"
                + " where evidence_id = ?::uuid and action = 'READ'",
            Integer.class,
            evidenceId);
    assertThat(reads).isEqualTo(1);
  }

  @Test
  void nonModeratorCannotReadEvidenceAndLeavesNoAuditRow() throws Exception {
    String owner = bearer("evidence-private-owner");
    String caseId = openDocumentCase(owner);
    String evidenceId = upload(owner, caseId);

    mockMvc
        .perform(
            get("/api/admin/verifications/" + caseId + "/evidence/" + evidenceId)
                .header("Authorization", owner))
        .andExpect(status().isForbidden());

    Integer reads =
        jdbcTemplate.queryForObject(
            "select count(*) from verification.verification_evidence_access_event"
                + " where evidence_id = ?::uuid and action = 'READ'",
            Integer.class,
            evidenceId);
    assertThat(reads).isZero();
  }

  @Test
  void documentCaseCannotBeApprovedUntilEvidenceIsUploaded() throws Exception {
    String owner = bearer("document-decision-owner");
    String admin = adminBearer("document-decision-admin");
    String caseId = openDocumentCase(owner);

    mockMvc
        .perform(
            post("/api/admin/verifications/" + caseId + "/approve")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"reasonCode\":\"DOCUMENT_OK\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("DOCUMENT_EVIDENCE_REQUIRED"));

    upload(owner, caseId);

    mockMvc
        .perform(
            post("/api/admin/verifications/" + caseId + "/approve")
                .header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0,\"reasonCode\":\"DOCUMENT_OK\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tier").value("DOCUMENT_VERIFIED"))
        .andExpect(jsonPath("$.badge.type").value("VERIFIED_OWNER"));
  }

  @Test
  void unsupportedUploadIsRejectedBeforeMetadataOrObjectAreStored() throws Exception {
    String owner = bearer("unsupported-evidence-owner");
    String caseId = openDocumentCase(owner);

    mockMvc
        .perform(
            multipart("/api/verifications/" + caseId + "/evidence")
                .file(
                    new MockMultipartFile(
                        "document", "private.txt", MediaType.TEXT_PLAIN_VALUE, SYNTHETIC))
                .header("Authorization", owner))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

    Integer evidenceRows =
        jdbcTemplate.queryForObject(
            "select count(*) from verification.verification_evidence where case_id = ?::uuid",
            Integer.class,
            caseId);
    assertThat(evidenceRows).isZero();
  }

  private String upload(String bearer, String caseId) throws Exception {
    String body =
        mockMvc
            .perform(
                multipart("/api/verifications/" + caseId + "/evidence")
                    .file(
                        new MockMultipartFile(
                            "document",
                            "private-lease.pdf",
                            MediaType.APPLICATION_PDF_VALUE,
                            SYNTHETIC))
                    .header("Authorization", bearer))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.contentType").value(MediaType.APPLICATION_PDF_VALUE))
            .andExpect(jsonPath("$.sizeBytes").value(SYNTHETIC.length))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body).doesNotContain("private-lease.pdf");
    assertThat(body).doesNotContain("storage");
    assertThat(body).doesNotContain("sha256");
    return JsonPath.read(body, "$.evidenceId");
  }

  private String openDocumentCase(String bearer) throws Exception {
    String propertyId = createProperty(bearer);
    String body =
        mockMvc
            .perform(
                post("/api/verifications")
                    .header("Authorization", bearer)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"propertyId\":\""
                            + propertyId
                            + "\",\"relationshipClaim\":\"OWNER\",\"method\":\"DOCUMENT\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.caseId");
  }

  private String createProperty(String bearer) throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/properties")
                    .header("Authorization", bearer)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"type\":\"BUILDING\",\"canonicalName\":\"Evidence Endpoint Tower "
                            + UUID.randomUUID()
                            + "\",\"allowDuplicate\":true}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.propertyId");
  }

  private String adminBearer(String subject) throws Exception {
    String token = bearer(subject);
    String accountId = accountIdOf(token);
    assertThat(
            jdbcTemplate.update(
                "update identity.account set role = 'ADMIN' where id = ?::uuid", accountId))
        .isEqualTo(1);
    return token;
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

  private static String bearer(String subject) {
    return "Bearer " + subject;
  }
}
