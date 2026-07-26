package com.example.geohousing.verification.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.verification.application.EvidenceStorageKey;
import com.example.geohousing.verification.application.EvidenceTooLargeException;
import com.example.geohousing.verification.application.StoredEvidence;
import com.example.geohousing.verification.domain.VerificationCaseId;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * The evidence storage adapter against a real S3 API (MinIO in Testcontainers), proving the round
 * trip that matters for retention: an object can be written, read back exactly, and — critically —
 * <em>actually deleted</em>, after which it is gone.
 *
 * <p>The fixtures are unmistakably synthetic (`.claude/rules/security.md`): never anything that
 * could be taken for a real document.
 */
@Testcontainers
class S3EvidenceStoreIntegrationTest {

  private static final String BUCKET = "verification-evidence";
  private static final byte[] SYNTHETIC =
      "SYNTHETIC-TEST-EVIDENCE-NOT-A-REAL-DOCUMENT".getBytes(StandardCharsets.UTF_8);

  // A local KMS key so MinIO honours the SSE-S3 (AES256) requests the adapter makes; managed S3
  // supports AES256 natively, but MinIO needs a key configured. Fixed non-secret test value.
  @Container
  static final MinIOContainer minio =
      new MinIOContainer("minio/minio:RELEASE.2025-04-08T15-41-24Z")
          .withEnv("MINIO_KMS_SECRET_KEY", "key1:AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=");

  private static S3Client s3;
  private static S3EvidenceStore store;

  @BeforeAll
  static void setUp() {
    s3 =
        S3Client.builder()
            .endpointOverride(URI.create(minio.getS3URL()))
            .region(Region.US_EAST_1)
            .forcePathStyle(true)
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(minio.getUserName(), minio.getPassword())))
            .build();
    s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());

    EvidenceStorageProperties properties =
        new EvidenceStorageProperties(
            minio.getS3URL(),
            "us-east-1",
            BUCKET,
            minio.getUserName(),
            minio.getPassword(),
            1024,
            true);
    store = new S3EvidenceStore(s3, properties);
  }

  @AfterAll
  static void tearDown() {
    if (s3 != null) {
      s3.close();
    }
  }

  private static EvidenceStorageKey freshKey() {
    return EvidenceStorageKey.mint(VerificationCaseId.of(UUID.randomUUID()));
  }

  @Test
  void storesReadsBackAndReportsWhatItStored() throws Exception {
    EvidenceStorageKey key = freshKey();

    StoredEvidence stored =
        store.put(key, "application/pdf", new ByteArrayInputStream(SYNTHETIC), 1024);

    assertThat(stored.key()).isEqualTo(key);
    assertThat(stored.contentType()).isEqualTo("application/pdf");
    assertThat(stored.sizeBytes()).isEqualTo(SYNTHETIC.length);
    // The checksum is of the bytes that actually landed.
    assertThat(stored.sha256()).hasSize(64).isEqualTo(sha256Hex(SYNTHETIC));
    assertThat(store.exists(key)).isTrue();

    try (InputStream read = store.read(key).orElseThrow()) {
      assertThat(read.readAllBytes()).isEqualTo(SYNTHETIC);
    }
  }

  @Test
  void deletingRemovesTheObjectAndIsIdempotent() {
    EvidenceStorageKey key = freshKey();
    store.put(key, "image/png", new ByteArrayInputStream(SYNTHETIC), 1024);
    assertThat(store.exists(key)).isTrue();

    store.delete(key);

    // Retention deletion actually removed it from storage.
    assertThat(store.exists(key)).isFalse();
    assertThat(store.read(key)).isEmpty();
    // Deleting again is safe — the sweep must be retryable.
    assertThatCode(() -> store.delete(key)).doesNotThrowAnyException();
  }

  @Test
  void readingAMissingObjectIsEmptyNotAnError() {
    assertThat(store.read(freshKey())).isEmpty();
    assertThat(store.exists(freshKey())).isFalse();
  }

  @Test
  void anOversizedUploadIsRefusedAndNothingIsStored() {
    EvidenceStorageKey key = freshKey();
    byte[] tooBig = new byte[2048];

    assertThatThrownBy(
            () -> store.put(key, "application/pdf", new ByteArrayInputStream(tooBig), 1024))
        .isInstanceOf(EvidenceTooLargeException.class);

    // The refusal happened before anything was written.
    assertThat(store.exists(key)).isFalse();
  }

  @Test
  void anEmptyUploadIsRefused() {
    assertThatThrownBy(
            () ->
                store.put(
                    freshKey(), "application/pdf", new ByteArrayInputStream(new byte[0]), 1024))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void objectsAreNotPublic() {
    // Written without any public-read ACL: an unauthenticated client cannot GET it.
    EvidenceStorageKey key = freshKey();
    s3.putObject(
        PutObjectRequest.builder().bucket(BUCKET).key(key.value()).build(),
        RequestBody.fromBytes(SYNTHETIC));

    try (S3Client anonymous =
        S3Client.builder()
            .endpointOverride(URI.create(minio.getS3URL()))
            .region(Region.US_EAST_1)
            .forcePathStyle(true)
            .credentialsProvider(
                StaticCredentialsProvider.create(AwsBasicCredentials.create("nobody", "nobody")))
            .build()) {
      assertThatThrownBy(
              () ->
                  anonymous.getObject(
                      software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
                          .bucket(BUCKET)
                          .key(key.value())
                          .build()))
          .isInstanceOf(software.amazon.awssdk.services.s3.model.S3Exception.class);
    }
  }

  private static String sha256Hex(byte[] bytes) throws Exception {
    return java.util.HexFormat.of()
        .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
  }
}
