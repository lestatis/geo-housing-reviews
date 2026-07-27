package com.example.geohousing.app.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.properties.domain.CreatorId;
import com.example.geohousing.properties.domain.Property;
import com.example.geohousing.properties.domain.PropertyId;
import com.example.geohousing.properties.domain.PropertyType;
import com.example.geohousing.verification.application.EvidenceContentGoneException;
import com.example.geohousing.verification.application.EvidenceNotFoundException;
import com.example.geohousing.verification.application.EvidenceRetentionService;
import com.example.geohousing.verification.application.EvidenceService;
import com.example.geohousing.verification.application.OpenVerificationCommand;
import com.example.geohousing.verification.application.VerificationDecisionService;
import com.example.geohousing.verification.application.VerificationSubmissionService;
import com.example.geohousing.verification.application.VerificationViewer;
import com.example.geohousing.verification.domain.AccountRef;
import com.example.geohousing.verification.domain.ModeratorId;
import com.example.geohousing.verification.domain.PropertyRef;
import com.example.geohousing.verification.domain.RelationshipClaim;
import com.example.geohousing.verification.domain.VerificationCase;
import com.example.geohousing.verification.domain.VerificationEvidence;
import com.example.geohousing.verification.domain.VerificationMethod;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Tier 2 evidence end to end through the real Spring wiring, with Postgres holding the metadata and
 * MinIO holding the bytes — the two consistency domains ADR-0008 introduced, exercised together.
 *
 * <p>Fixtures are unmistakably synthetic (`.claude/rules/security.md`).
 */
@Testcontainers
@SpringBootTest
class EvidencePersistenceIntegrationTest {

  private static final String BUCKET = "verification-evidence";
  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-23T10:00:00Z"), ZoneOffset.UTC);
  private static final byte[] SYNTHETIC =
      "SYNTHETIC-TEST-EVIDENCE-NOT-A-REAL-DOCUMENT".getBytes(StandardCharsets.UTF_8);

  @Container @ServiceConnection
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
          DockerImageName.parse("postgis/postgis:18-3.6").asCompatibleSubstituteFor("postgres"));

  // A local KMS key so MinIO honours the SSE-S3 requests the adapter makes; managed S3 supports
  // AES256 natively. Fixed non-secret test value.
  //
  // The bucket is created by MinIO itself at startup (a directory under its data root is a bucket),
  // so this test needs no storage SDK on its classpath — only the verification module's adapter
  // talks to S3, which is the boundary ADR-0008 draws.
  @Container
  static final MinIOContainer MINIO =
      new MinIOContainer("minio/minio:RELEASE.2025-04-08T15-41-24Z")
          .withEnv("MINIO_KMS_SECRET_KEY", "key1:AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=")
          .withCreateContainerCmdModifier(
              cmd ->
                  cmd.withEntrypoint(
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

  @Autowired private EvidenceService evidenceService;
  @Autowired private EvidenceRetentionService retentionService;
  @Autowired private VerificationDecisionService decisionService;
  @Autowired private VerificationSubmissionService submission;
  @Autowired private com.example.geohousing.properties.application.PropertyRepository properties;
  @Autowired private JdbcTemplate jdbcTemplate;

  private VerificationCase openDocumentCase(UUID account) {
    PropertyId propertyId = PropertyId.of(UUID.randomUUID());
    properties.create(
        Property.create(
            propertyId,
            PropertyType.BUILDING,
            "Evidence Tower " + UUID.randomUUID(),
            CreatorId.of(UUID.randomUUID()),
            CLOCK));
    return submission.open(
        new OpenVerificationCommand(
            AccountRef.of(account),
            PropertyRef.of(propertyId.value()),
            RelationshipClaim.OWNER,
            VerificationMethod.DOCUMENT));
  }

  private VerificationEvidence attach(VerificationCase documentCase, UUID owner) {
    return evidenceService.attach(
        documentCase.id(),
        VerificationViewer.account(AccountRef.of(owner)),
        "application/pdf",
        new ByteArrayInputStream(SYNTHETIC));
  }

  @Test
  void attachingWritesMetadataToPostgresAndBytesToTheObjectStore() {
    UUID owner = UUID.randomUUID();
    VerificationCase documentCase = openDocumentCase(owner);

    VerificationEvidence evidence = attach(documentCase, owner);

    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            "select storage_key, content_type, size_bytes, sha256, deleted_at"
                + " from verification.verification_evidence where id = ?::uuid",
            evidence.id().value());
    assertThat(row.get("content_type")).isEqualTo("application/pdf");
    assertThat(((Number) row.get("size_bytes")).longValue()).isEqualTo(SYNTHETIC.length);
    assertThat(row.get("deleted_at")).isNull();
    // Postgres holds the locator, never the document.
    assertThat(row.get("storage_key").toString())
        .startsWith("evidence/" + documentCase.id().value() + "/");
  }

  @Test
  void aModeratorReadReturnsTheBytesAndLeavesAnAuditRow() throws IOException {
    UUID owner = UUID.randomUUID();
    UUID moderator = UUID.randomUUID();
    VerificationCase documentCase = openDocumentCase(owner);
    VerificationEvidence evidence = attach(documentCase, owner);

    try (InputStream read =
        evidenceService.read(
            evidence.id(), VerificationViewer.moderator(AccountRef.of(moderator)))) {
      assertThat(read.readAllBytes()).isEqualTo(SYNTHETIC);
    }

    Map<String, Object> audit =
        jdbcTemplate.queryForMap(
            "select action, accessor_account_id::text as accessor"
                + " from verification.verification_evidence_access_event"
                + " where evidence_id = ?::uuid",
            evidence.id().value());
    assertThat(audit.get("action")).isEqualTo("READ");
    assertThat(audit.get("accessor")).isEqualTo(moderator.toString());
  }

  @Test
  void deletingACasesEvidenceRemovesTheObjectAndStampsTheRow() {
    UUID owner = UUID.randomUUID();
    UUID moderator = UUID.randomUUID();
    VerificationCase documentCase = openDocumentCase(owner);
    VerificationEvidence evidence = attach(documentCase, owner);

    assertThat(evidenceService.deleteForCase(documentCase.id(), moderator)).isEqualTo(1);

    // The row survives, stamped — proof of what was held and released.
    assertThat(
            jdbcTemplate.queryForObject(
                "select deleted_at is not null from verification.verification_evidence"
                    + " where id = ?::uuid",
                Boolean.class,
                evidence.id().value()))
        .isTrue();
    // And the document is genuinely gone: a moderator is told so plainly.
    assertThatThrownBy(
            () ->
                evidenceService.read(
                    evidence.id(), VerificationViewer.moderator(AccountRef.of(moderator))))
        .isInstanceOf(EvidenceContentGoneException.class);
  }

  @Test
  void cancellingACaseDeletesEvidenceAndAuditsTheOwner() {
    UUID owner = UUID.randomUUID();
    VerificationCase documentCase = openDocumentCase(owner);
    VerificationEvidence evidence = attach(documentCase, owner);

    submission.cancel(documentCase.id(), VerificationViewer.account(AccountRef.of(owner)));

    assertDeleted(evidence, owner);
  }

  @Test
  void approvingACaseDeletesEvidenceAndAuditsTheModerator() {
    UUID owner = UUID.randomUUID();
    UUID moderator = UUID.randomUUID();
    VerificationCase documentCase = openDocumentCase(owner);
    VerificationEvidence evidence = attach(documentCase, owner);

    decisionService
        .approve(
            ModeratorId.of(moderator),
            documentCase.id(),
            documentCase.version(),
            "DOCUMENT_OK",
            null)
        .orElseThrow();

    assertDeleted(evidence, moderator);
  }

  @Test
  void theRetentionSweepDeletesLapsedEvidenceAndAuditsWithoutAnActor() {
    UUID owner = UUID.randomUUID();
    VerificationCase documentCase = openDocumentCase(owner);
    VerificationEvidence evidence = attach(documentCase, owner);

    // Bring the deadline forward so the sweep selects it.
    jdbcTemplate.update(
        "update verification.verification_evidence set retention_deadline = now() - interval '1 day'"
            + " where id = ?::uuid",
        evidence.id().value());

    assertThat(retentionService.deleteLapsed(50)).isGreaterThanOrEqualTo(1);

    assertThat(
            jdbcTemplate.queryForObject(
                "select deleted_at is not null from verification.verification_evidence"
                    + " where id = ?::uuid",
                Boolean.class,
                evidence.id().value()))
        .isTrue();
    // A system sweep records no accessor — the one action the V5.2 check allows that for.
    Map<String, Object> audit =
        jdbcTemplate.queryForMap(
            "select action, accessor_account_id::text as accessor"
                + " from verification.verification_evidence_access_event"
                + " where evidence_id = ?::uuid and action = 'DELETE'",
            evidence.id().value());
    assertThat(audit.get("accessor")).isNull();
  }

  @Test
  void aNonModeratorIsNotToldEvidenceExists() {
    UUID owner = UUID.randomUUID();
    VerificationCase documentCase = openDocumentCase(owner);
    VerificationEvidence evidence = attach(documentCase, owner);

    // Even its own uploader cannot read it back, and is told nothing about it.
    assertThatThrownBy(
            () ->
                evidenceService.read(
                    evidence.id(), VerificationViewer.account(AccountRef.of(owner))))
        .isInstanceOf(EvidenceNotFoundException.class);

    Integer auditRows =
        jdbcTemplate.queryForObject(
            "select count(*) from verification.verification_evidence_access_event"
                + " where evidence_id = ?::uuid",
            Integer.class,
            evidence.id().value());
    assertThat(auditRows).isZero();
  }

  private void assertDeleted(VerificationEvidence evidence, UUID actor) {
    assertThat(
            jdbcTemplate.queryForObject(
                "select deleted_at is not null from verification.verification_evidence"
                    + " where id = ?::uuid",
                Boolean.class,
                evidence.id().value()))
        .isTrue();
    assertThatThrownBy(
            () ->
                evidenceService.read(
                    evidence.id(), VerificationViewer.moderator(AccountRef.of(actor))))
        .isInstanceOf(EvidenceContentGoneException.class);
    assertThat(
            jdbcTemplate.queryForObject(
                "select accessor_account_id::text from verification.verification_evidence_access_event"
                    + " where evidence_id = ?::uuid and action = 'DELETE'",
                String.class,
                evidence.id().value()))
        .isEqualTo(actor.toString());
  }
}
