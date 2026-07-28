package com.example.geohousing.moderation.domain;

import java.util.Objects;
import java.util.UUID;

/** Opaque, stable identifier for a {@link Report}. */
public record ReportId(UUID value) {

  public ReportId {
    Objects.requireNonNull(value, "report id value must not be null");
  }

  public static ReportId of(UUID value) {
    return new ReportId(value);
  }
}
