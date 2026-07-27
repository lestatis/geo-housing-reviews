package com.example.geohousing.verification.domain;

import java.util.Objects;
import java.util.UUID;

/** Identity of a piece of verification evidence. */
public record EvidenceId(UUID value) {

  public EvidenceId {
    Objects.requireNonNull(value, "value");
  }

  public static EvidenceId of(UUID value) {
    return new EvidenceId(value);
  }
}
