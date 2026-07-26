package com.example.geohousing.verification.infrastructure.storage;

import com.example.geohousing.verification.application.EvidenceStorageKey;
import com.example.geohousing.verification.application.EvidenceStore;
import com.example.geohousing.verification.application.EvidenceTooLargeException;
import com.example.geohousing.verification.application.StoredEvidence;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

/**
 * Stores verification evidence in an S3-compatible bucket (ADR-0008). The only class in the
 * codebase that touches a storage SDK.
 *
 * <p>Objects are written with server-side encryption and are never made public: no ACL is set, no
 * URL is produced here, and reads go through this adapter so the application can audit them.
 *
 * <p>Nothing in this class logs object content, and exceptions are deliberately not enriched with
 * bytes or keys beyond what the caller already has (SECURITY_PRIVACY.md §5).
 */
@Component
public class S3EvidenceStore implements EvidenceStore {

  private final S3Client s3;
  private final EvidenceStorageProperties properties;

  public S3EvidenceStore(S3Client s3, EvidenceStorageProperties properties) {
    this.s3 = Objects.requireNonNull(s3, "s3");
    this.properties = Objects.requireNonNull(properties, "properties");
  }

  /**
   * Reads the stream fully (bounded by {@code sizeLimitBytes}) before writing, so the size and
   * checksum recorded are of the bytes that actually landed rather than anything the client
   * claimed, and so an oversized upload is refused before a partial object exists in the bucket.
   */
  @Override
  public StoredEvidence put(
      EvidenceStorageKey key, String contentType, InputStream content, long sizeLimitBytes) {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(contentType, "contentType");
    Objects.requireNonNull(content, "content");

    byte[] bytes = readAtMost(content, sizeLimitBytes);
    if (bytes.length == 0) {
      throw new IllegalArgumentException("evidence must not be empty");
    }

    s3.putObject(
        PutObjectRequest.builder()
            .bucket(properties.bucket())
            .key(key.value())
            .contentType(contentType)
            .contentLength((long) bytes.length)
            .serverSideEncryption(ServerSideEncryption.AES256)
            .build(),
        RequestBody.fromInputStream(new ByteArrayInputStream(bytes), bytes.length));

    return new StoredEvidence(key, contentType, bytes.length, sha256(bytes));
  }

  @Override
  public Optional<InputStream> read(EvidenceStorageKey key) {
    Objects.requireNonNull(key, "key");
    try {
      ResponseInputStream<?> stream =
          s3.getObject(
              GetObjectRequest.builder().bucket(properties.bucket()).key(key.value()).build());
      return Optional.of(stream);
    } catch (NoSuchKeyException absent) {
      // Normal after retention deletion, not an error.
      return Optional.empty();
    }
  }

  /** Idempotent: S3 delete of a missing key succeeds, which is what a retryable sweep needs. */
  @Override
  public void delete(EvidenceStorageKey key) {
    Objects.requireNonNull(key, "key");
    s3.deleteObject(
        DeleteObjectRequest.builder().bucket(properties.bucket()).key(key.value()).build());
  }

  @Override
  public boolean exists(EvidenceStorageKey key) {
    Objects.requireNonNull(key, "key");
    try {
      s3.headObject(
          HeadObjectRequest.builder().bucket(properties.bucket()).key(key.value()).build());
      return true;
    } catch (NoSuchKeyException absent) {
      return false;
    }
  }

  /**
   * Reads up to the limit and refuses anything larger. Reads one byte past the limit deliberately:
   * that is how an over-limit stream is detected rather than silently truncated.
   */
  private static byte[] readAtMost(InputStream content, long sizeLimitBytes) {
    if (sizeLimitBytes <= 0) {
      throw new IllegalArgumentException("size limit must be positive");
    }
    try {
      byte[] bytes = content.readNBytes(Math.toIntExact(sizeLimitBytes) + 1);
      if (bytes.length > sizeLimitBytes) {
        throw new EvidenceTooLargeException(sizeLimitBytes);
      }
      return bytes;
    } catch (IOException exception) {
      throw new UncheckedIOException("could not read the uploaded evidence", exception);
    }
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is required by the platform", impossible);
    }
  }
}
