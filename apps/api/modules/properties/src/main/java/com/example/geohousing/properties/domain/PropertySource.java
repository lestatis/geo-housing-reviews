package com.example.geohousing.properties.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * A provenance record: where a piece of property information came from and when it was observed.
 * Recording a source does not make the information authoritative (see {@code
 * docs/DOMAIN_MODEL.md}).
 */
public record PropertySource(String sourceType, Instant observedAt, String note) {

  public PropertySource {
    if (sourceType == null || sourceType.isBlank()) {
      throw new IllegalArgumentException("sourceType must not be blank");
    }
    sourceType = sourceType.trim();
    Objects.requireNonNull(observedAt, "observedAt");
  }
}
