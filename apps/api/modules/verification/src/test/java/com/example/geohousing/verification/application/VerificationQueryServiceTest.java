package com.example.geohousing.verification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.verification.domain.AccountRef;
import com.example.geohousing.verification.domain.PropertyRef;
import com.example.geohousing.verification.domain.RelationshipClaim;
import com.example.geohousing.verification.domain.VerificationCase;
import com.example.geohousing.verification.domain.VerificationCaseId;
import com.example.geohousing.verification.domain.VerificationCaseNotFoundException;
import com.example.geohousing.verification.domain.VerificationMethod;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VerificationQueryServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-23T10:00:00Z"), ZoneOffset.UTC);
  private static final AccountRef OWNER = AccountRef.of(UUID.randomUUID());
  private static final AccountRef STRANGER = AccountRef.of(UUID.randomUUID());
  private static final PropertyRef PROPERTY = PropertyRef.of(UUID.randomUUID());

  private final InMemoryVerificationCaseRepository repository =
      new InMemoryVerificationCaseRepository();
  private final VerificationQueryService service = new VerificationQueryService(repository);

  private VerificationCase store(AccountRef account, PropertyRef property) {
    VerificationCase verificationCase =
        VerificationCase.open(
            VerificationCaseId.of(UUID.randomUUID()),
            account,
            property,
            RelationshipClaim.OWNER,
            VerificationMethod.INVITATION,
            1,
            CLOCK);
    repository.create(verificationCase);
    return verificationCase;
  }

  @Test
  void aCaseIsVisibleToItsOwnerAndModeratorsButNotAStranger() {
    VerificationCase verificationCase = store(OWNER, PROPERTY);

    assertThat(service.getById(verificationCase.id(), VerificationViewer.account(OWNER)).id())
        .isEqualTo(verificationCase.id());
    assertThat(service.getById(verificationCase.id(), VerificationViewer.moderator(STRANGER)).id())
        .isEqualTo(verificationCase.id());
    // A stranger is told it does not exist — a private workflow never confirms its existence.
    assertThatThrownBy(
            () -> service.getById(verificationCase.id(), VerificationViewer.account(STRANGER)))
        .isInstanceOf(VerificationCaseNotFoundException.class);
  }

  @Test
  void anUnknownCaseIsNotFound() {
    assertThatThrownBy(
            () ->
                service.getById(
                    VerificationCaseId.of(UUID.randomUUID()), VerificationViewer.moderator(OWNER)))
        .isInstanceOf(VerificationCaseNotFoundException.class);
  }

  @Test
  void anAccountFindsItsOwnLatestCaseForAProperty() {
    VerificationCase verificationCase = store(OWNER, PROPERTY);

    assertThat(service.findMine(OWNER, PROPERTY))
        .map(VerificationCase::id)
        .contains(verificationCase.id());
    assertThat(service.findMine(STRANGER, PROPERTY)).isEmpty();
  }

  @Test
  void thePendingQueueIsModeratorsOnly() {
    store(OWNER, PROPERTY);
    store(STRANGER, PropertyRef.of(UUID.randomUUID()));

    assertThat(service.pendingQueue(VerificationViewer.moderator(OWNER), null)).hasSize(2);
    assertThatThrownBy(() -> service.pendingQueue(VerificationViewer.account(OWNER), null))
        .isInstanceOf(VerificationAccessDeniedException.class);
  }

  @Test
  void theQueueSizeIsClamped() {
    for (int i = 0; i < VerificationQueryService.MAX_QUEUE_SIZE + 3; i++) {
      store(AccountRef.of(UUID.randomUUID()), PropertyRef.of(UUID.randomUUID()));
    }

    assertThat(service.pendingQueue(VerificationViewer.moderator(OWNER), 5000))
        .hasSize(VerificationQueryService.MAX_QUEUE_SIZE);
    assertThat(service.pendingQueue(VerificationViewer.moderator(OWNER), 0)).hasSize(1);
  }
}
