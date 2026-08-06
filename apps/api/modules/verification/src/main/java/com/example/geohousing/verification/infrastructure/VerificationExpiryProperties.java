package com.example.geohousing.verification.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How often lapsed verification badges are swept.
 *
 * <p>A badge with a {@code validThrough} is a statement that the relationship was checked and holds
 * until then. Nothing expires it on its own, so without this sweep the statement outlives its own
 * evidence — {@code docs/TRUST_VERIFICATION.md} is explicit that "verified" means checked, not
 * checked once.
 *
 * @param sweepBatchSize maximum badges one run may expire, so a backlog cannot monopolize a worker
 * @param sweepFixedDelay delay between runs
 */
@ConfigurationProperties(prefix = "verification.expiry")
public record VerificationExpiryProperties(int sweepBatchSize, Duration sweepFixedDelay) {

  public VerificationExpiryProperties {
    sweepBatchSize = sweepBatchSize <= 0 ? 100 : sweepBatchSize;
    sweepFixedDelay = sweepFixedDelay == null ? Duration.ofMinutes(15) : sweepFixedDelay;
  }
}
