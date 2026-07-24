package com.example.geohousing.verification.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.geohousing.verification.domain.AccountRef;
import com.example.geohousing.verification.domain.ModeratorId;
import com.example.geohousing.verification.domain.PropertyRef;
import com.example.geohousing.verification.domain.RelationshipClaim;
import com.example.geohousing.verification.domain.VerificationCase;
import com.example.geohousing.verification.domain.VerificationCaseId;
import com.example.geohousing.verification.domain.VerificationDecisionAction;
import com.example.geohousing.verification.domain.VerificationDecisionAuditEvent;
import com.example.geohousing.verification.domain.VerificationMethod;
import com.example.geohousing.verification.domain.VerificationStatus;
import com.example.geohousing.verification.domain.VerificationTier;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VerificationExpiryServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-23T10:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final ModeratorId MODERATOR = ModeratorId.of(UUID.randomUUID());

  private final InMemoryVerificationCaseRepository cases = new InMemoryVerificationCaseRepository();
  private final InMemoryVerificationCaseRepository.RecordingDecisionRepository decisions =
      cases.decisionRepository();
  private final RecordingProjection projection = new RecordingProjection();
  private final VerificationExpiryService service =
      new VerificationExpiryService(cases, decisions, projection, CLOCK);

  private static final class RecordingProjection implements ReviewProjection {
    private final List<VerificationTier> pushed = new ArrayList<>();

    @Override
    public void applyTier(AccountRef accountRef, PropertyRef propertyRef, VerificationTier tier) {
      pushed.add(tier);
    }
  }

  /** Stores an approved case, optionally with an expiry. */
  private VerificationCase storeApproved(Instant validThrough) {
    VerificationCase verificationCase =
        VerificationCase.open(
            VerificationCaseId.of(UUID.randomUUID()),
            AccountRef.of(UUID.randomUUID()),
            PropertyRef.of(UUID.randomUUID()),
            RelationshipClaim.CURRENT_RESIDENT,
            VerificationMethod.LOCATION_SIGNAL,
            1,
            CLOCK);
    verificationCase.approve(MODERATOR, "CLEAN", validThrough, CLOCK);
    cases.create(verificationCase);
    return verificationCase;
  }

  @Test
  void aLapsedBadgeExpiresRevertsTheTierAndIsAuditedWithoutAnActor() {
    VerificationCase lapsed = storeApproved(NOW.minusSeconds(60));

    int expired = service.expireLapsed(50);

    assertThat(expired).isEqualTo(1);
    VerificationCase stored = cases.findById(lapsed.id()).orElseThrow();
    assertThat(stored.status()).isEqualTo(VerificationStatus.EXPIRED);
    assertThat(stored.tier()).isEqualTo(VerificationTier.UNVERIFIED);
    assertThat(stored.badge()).isEmpty();

    VerificationDecisionAuditEvent event = decisions.events.get(0);
    assertThat(event.action()).isEqualTo(VerificationDecisionAction.EXPIRE);
    // A system sweep has no moderator; the schema allows an absent actor only here.
    assertThat(event.actorAccountId()).isEmpty();
    assertThat(event.reasonCode()).isEqualTo(VerificationExpiryService.EXPIRY_REASON_CODE);

    // The review falls back to unverified.
    assertThat(projection.pushed).containsExactly(VerificationTier.UNVERIFIED);
  }

  @Test
  void aBadgeWithNoExpiryNeverLapses() {
    storeApproved(null);

    assertThat(service.expireLapsed(50)).isZero();
    assertThat(decisions.events).isEmpty();
    assertThat(projection.pushed).isEmpty();
  }

  @Test
  void aBadgeStillWithinItsValidityIsLeftAlone() {
    VerificationCase current = storeApproved(NOW.plusSeconds(3600));

    assertThat(service.expireLapsed(50)).isZero();
    assertThat(cases.findById(current.id()).orElseThrow().status())
        .isEqualTo(VerificationStatus.APPROVED);
  }

  @Test
  void theSweepIsIdempotent() {
    storeApproved(NOW.minusSeconds(60));

    assertThat(service.expireLapsed(50)).isEqualTo(1);
    // An expired case is no longer approved, so a second run selects nothing.
    assertThat(service.expireLapsed(50)).isZero();
    assertThat(decisions.events).hasSize(1);
  }

  @Test
  void theSweepRespectsItsLimit() {
    for (int i = 1; i <= 5; i++) {
      storeApproved(NOW.minusSeconds(i * 60L));
    }

    assertThat(service.expireLapsed(2)).isEqualTo(2);
    assertThat(service.expireLapsed(50)).isEqualTo(3);
  }
}
