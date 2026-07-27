package com.example.geohousing.verification.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VerificationEvidenceTest {

  private static final Instant UPLOADED_AT = Instant.parse("2026-07-23T10:00:00Z");
  private static final Clock CLOCK = Clock.fixed(UPLOADED_AT, ZoneOffset.UTC);
  private static final Instant RETENTION = UPLOADED_AT.plusSeconds(30L * 24 * 3600);
  private static final String HASH = "a".repeat(64);

  private static VerificationEvidence record(Instant retentionDeadline) {
    return VerificationEvidence.record(
        EvidenceId.of(UUID.randomUUID()),
        VerificationCaseId.of(UUID.randomUUID()),
        "evidence/" + UUID.randomUUID() + "/key",
        "application/pdf",
        1234,
        HASH,
        retentionDeadline,
        CLOCK);
  }

  @Test
  void freshEvidenceIsNotDeletedAndCarriesItsUploadTime() {
    VerificationEvidence evidence = record(RETENTION);

    assertThat(evidence.isDeleted()).isFalse();
    assertThat(evidence.deletedAt()).isEmpty();
    assertThat(evidence.uploadedAt()).isEqualTo(UPLOADED_AT);
    assertThat(evidence.sizeBytes()).isEqualTo(1234);
  }

  @Test
  void metadataInvariantsMirrorTheSchema() {
    assertThatThrownBy(
            () ->
                VerificationEvidence.record(
                    EvidenceId.of(UUID.randomUUID()),
                    VerificationCaseId.of(UUID.randomUUID()),
                    "key",
                    "application/pdf",
                    0,
                    HASH,
                    RETENTION,
                    CLOCK))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                VerificationEvidence.record(
                    EvidenceId.of(UUID.randomUUID()),
                    VerificationCaseId.of(UUID.randomUUID()),
                    "key",
                    "application/pdf",
                    10,
                    "NOT-A-HEX-HASH",
                    RETENTION,
                    CLOCK))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aBadgeIsPastRetentionOnceItsDeadlineHasArrivedAndItIsNotYetDeleted() {
    VerificationEvidence evidence = record(RETENTION);

    assertThat(evidence.isPastRetention(RETENTION.minusSeconds(1))).isFalse();
    assertThat(evidence.isPastRetention(RETENTION)).isTrue();
    assertThat(evidence.isPastRetention(RETENTION.plusSeconds(1))).isTrue();
  }

  @Test
  void deletionMarksTheRowKeepsItAndIsIdempotent() {
    VerificationEvidence evidence = record(UPLOADED_AT.minusSeconds(1));
    assertThat(evidence.isPastRetention(UPLOADED_AT)).isTrue();

    Clock deletionClock = Clock.fixed(UPLOADED_AT.plusSeconds(10), ZoneOffset.UTC);
    evidence.markDeleted(deletionClock);

    assertThat(evidence.isDeleted()).isTrue();
    assertThat(evidence.deletedAt()).contains(UPLOADED_AT.plusSeconds(10));
    // Past-retention is false once deleted: the sweep will not select it again.
    assertThat(evidence.isPastRetention(UPLOADED_AT.plusSeconds(100))).isFalse();

    // Re-marking keeps the original deletion time — the sweep is safe to retry.
    evidence.markDeleted(Clock.fixed(UPLOADED_AT.plusSeconds(999), ZoneOffset.UTC));
    assertThat(evidence.deletedAt()).contains(UPLOADED_AT.plusSeconds(10));
  }
}
