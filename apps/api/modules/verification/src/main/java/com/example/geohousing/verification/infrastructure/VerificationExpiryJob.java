package com.example.geohousing.verification.infrastructure;

import com.example.geohousing.verification.application.VerificationExpiryService;
import java.util.Objects;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Invokes the idempotent badge-expiry sweep on a configured cadence.
 *
 * <p>The service existed and nothing in production called it: expiry ran only from tests, so a
 * lapsed badge stayed {@code APPROVED} indefinitely, kept projecting its tier onto the author's
 * reviews, and kept feeding ranking. A platform that says "verified" while the relationship it
 * checked has expired is asserting something it no longer knows.
 *
 * <p>The application service owns the transition and its audit; this adapter only supplies the
 * trigger, exactly as {@link EvidenceRetentionJob} does for retention.
 */
@Component
public class VerificationExpiryJob {

  private final VerificationExpiryService expiryService;
  private final VerificationExpiryProperties properties;

  public VerificationExpiryJob(
      VerificationExpiryService expiryService, VerificationExpiryProperties properties) {
    this.expiryService = Objects.requireNonNull(expiryService, "expiryService");
    this.properties = Objects.requireNonNull(properties, "properties");
  }

  @Scheduled(fixedDelayString = "${verification.expiry.sweep-fixed-delay}")
  public void expireLapsedBadges() {
    expiryService.expireLapsed(properties.sweepBatchSize());
  }
}
