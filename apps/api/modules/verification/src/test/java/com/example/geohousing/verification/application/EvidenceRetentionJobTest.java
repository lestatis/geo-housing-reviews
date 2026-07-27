package com.example.geohousing.verification.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.geohousing.verification.domain.EvidenceId;
import com.example.geohousing.verification.domain.VerificationCaseId;
import com.example.geohousing.verification.domain.VerificationEvidence;
import com.example.geohousing.verification.infrastructure.EvidenceRetentionJob;
import com.example.geohousing.verification.infrastructure.EvidenceRetentionProperties;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EvidenceRetentionJobTest {

  private static final Instant NOW = Instant.parse("2026-07-23T10:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  @Test
  void scheduledRunDelegatesToTheConfiguredBoundedSweep() {
    InMemoryEvidenceRepository repository = new InMemoryEvidenceRepository();
    InMemoryEvidenceStore store = new InMemoryEvidenceStore();
    RecordingEvidenceAccessAudit audit = new RecordingEvidenceAccessAudit();
    EvidenceRetentionService service =
        new EvidenceRetentionService(repository, audit, store, CLOCK);
    storeLapsed(repository, store);
    storeLapsed(repository, store);
    EvidenceRetentionJob job =
        new EvidenceRetentionJob(
            service, new EvidenceRetentionProperties(30, 7, 1, Duration.ofMinutes(15)));

    job.deleteLapsedEvidence();

    assertThat(store.objects).hasSize(1);
    assertThat(audit.events).hasSize(1);
  }

  private static void storeLapsed(
      InMemoryEvidenceRepository repository, InMemoryEvidenceStore store) {
    VerificationCaseId caseId = VerificationCaseId.of(UUID.randomUUID());
    EvidenceStorageKey key = EvidenceStorageKey.mint(caseId);
    store.put(key, "application/pdf", new ByteArrayInputStream("SYNTHETIC".getBytes()), 1024);
    repository.create(
        VerificationEvidence.record(
            EvidenceId.of(UUID.randomUUID()),
            caseId,
            key.value(),
            "application/pdf",
            9,
            "a".repeat(64),
            NOW.minusSeconds(1),
            CLOCK));
  }
}
