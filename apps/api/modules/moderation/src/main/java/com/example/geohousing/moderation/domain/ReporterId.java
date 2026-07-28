package com.example.geohousing.moderation.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * The account that filed a report.
 *
 * <p>Recorded so abuse of the reporting channel is traceable and so a reporter can follow their own
 * report. It is never exposed to the reported author or in any public representation:
 * SECURITY_PRIVACY.md treats attempts to identify critics as an abuse path in its own right.
 */
public record ReporterId(UUID value) {

  public ReporterId {
    Objects.requireNonNull(value, "reporter id value must not be null");
  }

  public static ReporterId of(UUID value) {
    return new ReporterId(value);
  }
}
