package com.example.geohousing.verification.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.geohousing.verification.domain.EvidenceAccessAction;
import com.example.geohousing.verification.domain.EvidenceId;
import com.example.geohousing.verification.domain.VerificationCaseId;
import com.example.geohousing.verification.domain.VerificationEvidence;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EvidenceRetentionServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-23T10:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private final InMemoryEvidenceRepository evidenceRepository = new InMemoryEvidenceRepository();
  private final RecordingEvidenceAccessAudit accessAudit = new RecordingEvidenceAccessAudit();
  private final InMemoryEvidenceStore store = new InMemoryEvidenceStore();
  private final EvidenceRetentionService service =
      new EvidenceRetentionService(evidenceRepository, accessAudit, store, CLOCK);

  /** Stores evidence whose object is present and whose deadline is where the caller wants it. */
  private VerificationEvidence store(Instant retentionDeadline) {
    VerificationCaseId caseId = VerificationCaseId.of(UUID.randomUUID());
    EvidenceStorageKey key = EvidenceStorageKey.mint(caseId);
    store.put(
        key,
        "application/pdf",
        new java.io.ByteArrayInputStream("SYNTHETIC-TEST-EVIDENCE".getBytes()),
        1024);
    VerificationEvidence evidence =
        VerificationEvidence.record(
            EvidenceId.of(UUID.randomUUID()),
            caseId,
            key.value(),
            "application/pdf",
            23,
            "a".repeat(64),
            retentionDeadline,
            CLOCK);
    evidenceRepository.create(evidence);
    return evidence;
  }

  @Test
  void lapsedEvidenceIsDeletedFromStorageStampedAndAuditedWithoutAnActor() {
    VerificationEvidence lapsed = store(NOW.minusSeconds(60));

    assertThat(service.deleteLapsed(50)).isEqualTo(1);

    // The document is genuinely gone from storage.
    assertThat(store.objects).isEmpty();
    // The metadata row survives as proof of completion.
    VerificationEvidence stored = evidenceRepository.findById(lapsed.id()).orElseThrow();
    assertThat(stored.isDeleted()).isTrue();
    assertThat(stored.deletedAt()).contains(NOW);
    // A system sweep has no human actor — the one case the schema allows that.
    assertThat(accessAudit.last().action()).isEqualTo(EvidenceAccessAction.DELETE);
    assertThat(accessAudit.last().accessorAccountId()).isEmpty();
  }

  @Test
  void evidenceStillWithinRetentionIsLeftAlone() {
    VerificationEvidence current = store(NOW.plusSeconds(3600));

    assertThat(service.deleteLapsed(50)).isZero();

    assertThat(store.objects).hasSize(1);
    assertThat(evidenceRepository.findById(current.id()).orElseThrow().isDeleted()).isFalse();
    assertThat(accessAudit.events).isEmpty();
  }

  @Test
  void theSweepIsIdempotent() {
    store(NOW.minusSeconds(60));

    assertThat(service.deleteLapsed(50)).isEqualTo(1);
    // A stamped row is no longer selected, so a second run finds nothing.
    assertThat(service.deleteLapsed(50)).isZero();
    assertThat(accessAudit.events).hasSize(1);
  }

  @Test
  void theSweepRespectsItsLimit() {
    for (int i = 1; i <= 5; i++) {
      store(NOW.minusSeconds(i * 60L));
    }

    assertThat(service.deleteLapsed(2)).isEqualTo(2);
    assertThat(service.deleteLapsed(50)).isEqualTo(3);
    assertThat(store.objects).isEmpty();
  }

  @Test
  void anAlreadyMissingObjectStillCompletesTheDeletion() {
    // The object vanished (an interrupted earlier run deleted it before stamping the row).
    VerificationEvidence lapsed = store(NOW.minusSeconds(60));
    store.objects.clear();

    assertThat(service.deleteLapsed(50)).isEqualTo(1);

    // The sweep converges: the row is stamped even though there was nothing left to delete.
    assertThat(evidenceRepository.findById(lapsed.id()).orElseThrow().isDeleted()).isTrue();
  }
}
