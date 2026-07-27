package com.example.geohousing.verification.application;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory {@link EvidenceStore} for use-case tests. Mirrors the real adapter's contract where the
 * tests depend on it: the size cap is enforced while reading, delete is idempotent, and reading a
 * missing object is empty rather than an error. The real S3 behaviour is proven separately against
 * MinIO.
 */
final class InMemoryEvidenceStore implements EvidenceStore {

  final Map<String, byte[]> objects = new LinkedHashMap<>();

  @Override
  public StoredEvidence put(
      EvidenceStorageKey key, String contentType, InputStream content, long sizeLimitBytes) {
    byte[] bytes;
    try {
      bytes = content.readNBytes(Math.toIntExact(sizeLimitBytes) + 1);
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
    if (bytes.length > sizeLimitBytes) {
      throw new EvidenceTooLargeException(sizeLimitBytes);
    }
    if (bytes.length == 0) {
      throw new IllegalArgumentException("evidence must not be empty");
    }
    objects.put(key.value(), bytes);
    return new StoredEvidence(key, contentType, bytes.length, sha256(bytes));
  }

  @Override
  public Optional<InputStream> read(EvidenceStorageKey key) {
    return Optional.ofNullable(objects.get(key.value())).map(ByteArrayInputStream::new);
  }

  @Override
  public void delete(EvidenceStorageKey key) {
    objects.remove(key.value());
  }

  @Override
  public boolean exists(EvidenceStorageKey key) {
    return objects.containsKey(key.value());
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
