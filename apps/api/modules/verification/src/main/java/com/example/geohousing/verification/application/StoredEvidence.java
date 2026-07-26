package com.example.geohousing.verification.application;

import java.util.Objects;

/**
 * What the store reports back about an object it just accepted. The checksum lets the metadata row
 * record what was stored without keeping the bytes, so a later read can be checked against it.
 */
public record StoredEvidence(
    EvidenceStorageKey key, String contentType, long sizeBytes, String sha256) {

  public StoredEvidence {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(contentType, "contentType");
    Objects.requireNonNull(sha256, "sha256");
    if (sizeBytes <= 0) {
      throw new IllegalArgumentException("stored evidence must not be empty");
    }
  }
}
