package com.example.geohousing.verification.domain;

import java.util.Objects;
import java.util.UUID;

/** Identity of a verification case. */
public record VerificationCaseId(UUID value) {

  public VerificationCaseId {
    Objects.requireNonNull(value, "value");
  }

  public static VerificationCaseId of(UUID value) {
    return new VerificationCaseId(value);
  }
}
