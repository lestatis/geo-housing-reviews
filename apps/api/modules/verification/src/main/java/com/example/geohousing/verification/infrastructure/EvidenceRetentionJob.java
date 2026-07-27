package com.example.geohousing.verification.infrastructure;

import com.example.geohousing.verification.application.EvidenceRetentionService;
import java.util.Objects;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Invokes the idempotent evidence-retention sweep on a configured cadence. The application service
 * owns deletion ordering and the append-only DELETE audit; this adapter only supplies the trigger.
 */
@Component
public class EvidenceRetentionJob {

  private final EvidenceRetentionService retentionService;
  private final EvidenceRetentionProperties properties;

  public EvidenceRetentionJob(
      EvidenceRetentionService retentionService, EvidenceRetentionProperties properties) {
    this.retentionService = Objects.requireNonNull(retentionService, "retentionService");
    this.properties = Objects.requireNonNull(properties, "properties");
  }

  @Scheduled(fixedDelayString = "${verification.evidence.retention.sweep-fixed-delay}")
  public void deleteLapsedEvidence() {
    retentionService.deleteLapsed(properties.sweepBatchSize());
  }
}
