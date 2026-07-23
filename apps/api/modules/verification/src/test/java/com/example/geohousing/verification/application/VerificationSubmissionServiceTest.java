package com.example.geohousing.verification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.verification.domain.AccountRef;
import com.example.geohousing.verification.domain.IllegalVerificationStateTransitionException;
import com.example.geohousing.verification.domain.PropertyRef;
import com.example.geohousing.verification.domain.RelationshipClaim;
import com.example.geohousing.verification.domain.VerificationCase;
import com.example.geohousing.verification.domain.VerificationCaseId;
import com.example.geohousing.verification.domain.VerificationCaseNotFoundException;
import com.example.geohousing.verification.domain.VerificationMethod;
import com.example.geohousing.verification.domain.VerificationStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VerificationSubmissionServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-23T10:00:00Z"), ZoneOffset.UTC);
  private static final AccountRef ACCOUNT = AccountRef.of(UUID.randomUUID());
  private static final AccountRef STRANGER = AccountRef.of(UUID.randomUUID());
  private static final PropertyRef PROPERTY = PropertyRef.of(UUID.randomUUID());

  private final InMemoryVerificationCaseRepository repository =
      new InMemoryVerificationCaseRepository();

  private VerificationSubmissionService serviceSeeing(PropertyLookup lookup) {
    return new VerificationSubmissionService(repository, lookup, CLOCK);
  }

  private VerificationSubmissionService service() {
    return serviceSeeing(Optional::of);
  }

  private static OpenVerificationCommand command() {
    return new OpenVerificationCommand(
        ACCOUNT, PROPERTY, RelationshipClaim.CURRENT_RESIDENT, VerificationMethod.INVITATION);
  }

  @Test
  void openingLeavesAPendingCaseForAModeratorToDecide() {
    VerificationCase opened = service().open(command());

    assertThat(opened.status()).isEqualTo(VerificationStatus.PENDING);
    assertThat(opened.accountRef()).isEqualTo(ACCOUNT);
    assertThat(opened.badge()).isEmpty();
    assertThat(repository.byId).containsKey(opened.id());
  }

  @Test
  void aCaseForAnUnknownPropertyIsRefused() {
    VerificationSubmissionService service = serviceSeeing(ref -> Optional.empty());

    assertThatThrownBy(() -> service.open(command()))
        .isInstanceOf(PropertyNotFoundForVerificationException.class);
    assertThat(repository.byId).isEmpty();
  }

  @Test
  void aCaseAttachesToTheSurvivingPropertyAfterAMerge() {
    PropertyRef survivor = PropertyRef.of(UUID.randomUUID());
    VerificationSubmissionService service = serviceSeeing(ref -> Optional.of(survivor));

    VerificationCase opened = service.open(command());

    assertThat(opened.propertyRef()).isEqualTo(survivor);
  }

  @Test
  void anAccountCannotHoldTwoLiveCasesForTheSameProperty() {
    VerificationSubmissionService service = service();
    VerificationCase first = service.open(command());

    assertThatThrownBy(() -> service.open(command()))
        .isInstanceOf(DuplicateVerificationCaseException.class)
        .extracting(t -> ((DuplicateVerificationCaseException) t).existingCaseId())
        .isEqualTo(first.id());
    assertThat(repository.byId).hasSize(1);
  }

  @Test
  void anApprovedCaseStillBlocksASecondOne() {
    VerificationSubmissionService service = service();
    VerificationCase first = service.open(command());
    // Approve it directly in the store, then a fresh open must still be refused.
    VerificationCase loaded = repository.findById(first.id()).orElseThrow();
    loaded.approve(
        com.example.geohousing.verification.domain.ModeratorId.of(UUID.randomUUID()),
        "CLEAN",
        null,
        CLOCK);
    repository.save(loaded);

    assertThatThrownBy(() -> service.open(command()))
        .isInstanceOf(DuplicateVerificationCaseException.class);
  }

  @Test
  void aRejectedCaseFreesTheSlotForAFreshOne() {
    VerificationSubmissionService service = service();
    VerificationCase first = service.open(command());
    VerificationCase loaded = repository.findById(first.id()).orElseThrow();
    loaded.reject(
        com.example.geohousing.verification.domain.ModeratorId.of(UUID.randomUUID()),
        "NO_EVIDENCE",
        CLOCK);
    repository.save(loaded);

    VerificationCase second = service.open(command());

    assertThat(second.id()).isNotEqualTo(first.id());
    assertThat(repository.byId).hasSize(2);
  }

  @Test
  void onlyTheOwnerCanCancelTheirCase() {
    VerificationSubmissionService service = service();
    VerificationCase opened = service.open(command());

    assertThatThrownBy(() -> service.cancel(opened.id(), VerificationViewer.account(STRANGER)))
        .isInstanceOf(VerificationCaseNotFoundException.class);

    VerificationCase cancelled = service.cancel(opened.id(), VerificationViewer.account(ACCOUNT));
    assertThat(cancelled.status()).isEqualTo(VerificationStatus.CANCELLED);
  }

  @Test
  void aModeratorSeesAStrangersCaseButStillCannotCancelIt() {
    VerificationSubmissionService service = service();
    VerificationCase opened = service.open(command());

    // Visible to a moderator (not 404), but cancellation is the owner's action — a moderator
    // withdrawing a case is a reject, which is audited.
    assertThatThrownBy(() -> service.cancel(opened.id(), VerificationViewer.moderator(STRANGER)))
        .isInstanceOf(VerificationAccessDeniedException.class);
  }

  @Test
  void cancellingACaseThatDoesNotExistIsNotFound() {
    assertThatThrownBy(
            () ->
                service()
                    .cancel(
                        VerificationCaseId.of(UUID.randomUUID()),
                        VerificationViewer.account(ACCOUNT)))
        .isInstanceOf(VerificationCaseNotFoundException.class);
  }

  @Test
  void anApprovedCaseCannotBeCancelled() {
    VerificationSubmissionService service = service();
    VerificationCase opened = service.open(command());
    VerificationCase loaded = repository.findById(opened.id()).orElseThrow();
    loaded.approve(
        com.example.geohousing.verification.domain.ModeratorId.of(UUID.randomUUID()),
        "CLEAN",
        null,
        CLOCK);
    repository.save(loaded);

    assertThatThrownBy(() -> service.cancel(opened.id(), VerificationViewer.account(ACCOUNT)))
        .isInstanceOf(IllegalVerificationStateTransitionException.class);
  }
}
