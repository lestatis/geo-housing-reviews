package com.example.geohousing.verification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.verification.domain.AccountRef;
import com.example.geohousing.verification.domain.IllegalVerificationStateTransitionException;
import com.example.geohousing.verification.domain.ModeratorId;
import com.example.geohousing.verification.domain.PropertyRef;
import com.example.geohousing.verification.domain.RelationshipClaim;
import com.example.geohousing.verification.domain.VerificationCase;
import com.example.geohousing.verification.domain.VerificationCaseId;
import com.example.geohousing.verification.domain.VerificationDecisionAction;
import com.example.geohousing.verification.domain.VerificationDecisionAuditEvent;
import com.example.geohousing.verification.domain.VerificationDecisionOutcome;
import com.example.geohousing.verification.domain.VerificationMethod;
import com.example.geohousing.verification.domain.VerificationStatus;
import com.example.geohousing.verification.domain.VerificationTier;
import com.example.geohousing.verification.domain.VerificationVersionConflictException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VerificationDecisionServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-23T10:00:00Z"), ZoneOffset.UTC);
  private static final ModeratorId MODERATOR = ModeratorId.of(UUID.randomUUID());

  private final InMemoryVerificationCaseRepository cases = new InMemoryVerificationCaseRepository();
  private final InMemoryVerificationCaseRepository.RecordingDecisionRepository decisions =
      cases.decisionRepository();
  private final VerificationDecisionService service =
      new VerificationDecisionService(cases, decisions, CLOCK);

  private VerificationCase storePending() {
    VerificationCase verificationCase =
        VerificationCase.open(
            VerificationCaseId.of(UUID.randomUUID()),
            AccountRef.of(UUID.randomUUID()),
            PropertyRef.of(UUID.randomUUID()),
            RelationshipClaim.CURRENT_RESIDENT,
            VerificationMethod.BUILDING_CODE,
            1,
            CLOCK);
    cases.create(verificationCase);
    return verificationCase;
  }

  private VerificationDecisionAuditEvent lastEvent() {
    return decisions.events.get(decisions.events.size() - 1);
  }

  @Test
  void approvingGrantsTheTierAndWritesTheAuditRow() {
    VerificationCase pending = storePending();

    VerificationCase approved =
        service
            .approve(MODERATOR, pending.id(), pending.version(), "INVITE_CONFIRMED", null)
            .orElseThrow();

    assertThat(approved.status()).isEqualTo(VerificationStatus.APPROVED);
    assertThat(approved.tier()).isEqualTo(VerificationTier.RELATIONSHIP_SIGNAL);
    assertThat(cases.findById(pending.id()).orElseThrow().status())
        .isEqualTo(VerificationStatus.APPROVED);

    VerificationDecisionAuditEvent event = lastEvent();
    assertThat(event.action()).isEqualTo(VerificationDecisionAction.APPROVE);
    assertThat(event.outcome()).isEqualTo(VerificationDecisionOutcome.APPLIED);
    assertThat(event.actorAccountId()).contains(MODERATOR.value());
    assertThat(event.reasonCode()).isEqualTo("INVITE_CONFIRMED");
  }

  @Test
  void rejectingGrantsNoTierButIsStillAudited() {
    VerificationCase pending = storePending();

    service.reject(MODERATOR, pending.id(), pending.version(), "NO_EVIDENCE");

    assertThat(cases.findById(pending.id()).orElseThrow().status())
        .isEqualTo(VerificationStatus.REJECTED);
    assertThat(lastEvent().action()).isEqualTo(VerificationDecisionAction.REJECT);
  }

  @Test
  void aDecisionAgainstAMissingCaseIsAuditedAsNotFound() {
    VerificationCaseId missing = VerificationCaseId.of(UUID.randomUUID());

    assertThat(service.approve(MODERATOR, missing, 0L, "CLEAN", null)).isEmpty();

    VerificationDecisionAuditEvent event = lastEvent();
    assertThat(event.outcome()).isEqualTo(VerificationDecisionOutcome.NOT_FOUND);
    assertThat(event.caseId()).isEqualTo(missing);
  }

  @Test
  void aStaleVersionIsRefusedBeforeAnythingChanges() {
    VerificationCase pending = storePending();

    assertThatThrownBy(
            () -> service.approve(MODERATOR, pending.id(), pending.version() + 1, "CLEAN", null))
        .isInstanceOf(VerificationVersionConflictException.class);

    assertThat(cases.findById(pending.id()).orElseThrow().status())
        .isEqualTo(VerificationStatus.PENDING);
    assertThat(decisions.events).isEmpty();
  }

  @Test
  void decidingAnAlreadyDecidedCaseIsAStateConflictWithNoSecondAuditRow() {
    VerificationCase pending = storePending();
    service.approve(MODERATOR, pending.id(), pending.version(), "CLEAN", null);
    long version = cases.findById(pending.id()).orElseThrow().version();

    assertThatThrownBy(() -> service.approve(MODERATOR, pending.id(), version, "CLEAN", null))
        .isInstanceOf(IllegalVerificationStateTransitionException.class);
    assertThat(decisions.events).hasSize(1);
  }

  @Test
  void aDecisionWithoutAReasonCodeIsRefusedBeforeAnyStateIsTouched() {
    VerificationCase pending = storePending();

    for (String blank : new String[] {null, "", "   "}) {
      assertThatThrownBy(() -> service.reject(MODERATOR, pending.id(), pending.version(), blank))
          .isInstanceOf(IllegalArgumentException.class);
    }
    assertThat(cases.findById(pending.id()).orElseThrow().status())
        .isEqualTo(VerificationStatus.PENDING);
    assertThat(decisions.events).isEmpty();
  }
}
