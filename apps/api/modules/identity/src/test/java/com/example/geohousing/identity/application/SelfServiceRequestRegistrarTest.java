package com.example.geohousing.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.SelfServiceRequest;
import com.example.geohousing.identity.domain.SelfServiceRequestAlreadyExistsException;
import com.example.geohousing.identity.domain.SelfServiceRequestType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SelfServiceRequestRegistrarTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-15T10:00:00Z"), ZoneOffset.UTC);
  private static final AccountId ACCOUNT_ID = AccountId.of(UUID.randomUUID());

  @Test
  void firstUseOfAKeyRecordsItAndReturnsFirst() {
    InMemoryRepo repo = new InMemoryRepo();
    SelfServiceRequestRegistrar registrar = new SelfServiceRequestRegistrar(repo, CLOCK);

    SelfServiceRequestRegistrar.Outcome outcome =
        registrar.register(ACCOUNT_ID, "key-1", SelfServiceRequestType.EXPORT);

    assertThat(outcome).isEqualTo(SelfServiceRequestRegistrar.Outcome.FIRST);
    assertThat(repo.records).hasSize(1);
  }

  @Test
  void sameKeyAndTypeReturnsReplayWithoutRecordingAgain() {
    InMemoryRepo repo = new InMemoryRepo();
    SelfServiceRequestRegistrar registrar = new SelfServiceRequestRegistrar(repo, CLOCK);
    registrar.register(ACCOUNT_ID, "key-1", SelfServiceRequestType.DELETE);

    SelfServiceRequestRegistrar.Outcome outcome =
        registrar.register(ACCOUNT_ID, "key-1", SelfServiceRequestType.DELETE);

    assertThat(outcome).isEqualTo(SelfServiceRequestRegistrar.Outcome.REPLAY);
    assertThat(repo.records).hasSize(1);
  }

  @Test
  void sameKeyDifferentTypeIsAConflict() {
    InMemoryRepo repo = new InMemoryRepo();
    SelfServiceRequestRegistrar registrar = new SelfServiceRequestRegistrar(repo, CLOCK);
    registrar.register(ACCOUNT_ID, "key-1", SelfServiceRequestType.EXPORT);

    assertThatThrownBy(() -> registrar.register(ACCOUNT_ID, "key-1", SelfServiceRequestType.DELETE))
        .isInstanceOf(IdempotencyKeyConflictException.class);
  }

  @Test
  void rejectsABlankKey() {
    SelfServiceRequestRegistrar registrar =
        new SelfServiceRequestRegistrar(new InMemoryRepo(), CLOCK);

    assertThatThrownBy(() -> registrar.register(ACCOUNT_ID, " ", SelfServiceRequestType.EXPORT))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void resolvesAConcurrentInsertRaceByRereadingTheWinner() {
    // record() throws as if another request inserted the same key first; the row is then visible.
    InMemoryRepo repo =
        new InMemoryRepo() {
          @Override
          public void record(SelfServiceRequest request) {
            records.add(
                SelfServiceRequest.reconstitute(
                    UUID.randomUUID(),
                    request.accountId(),
                    request.idempotencyKey(),
                    SelfServiceRequestType.EXPORT,
                    request.createdAt()));
            throw new SelfServiceRequestAlreadyExistsException("raced");
          }
        };
    SelfServiceRequestRegistrar registrar = new SelfServiceRequestRegistrar(repo, CLOCK);

    SelfServiceRequestRegistrar.Outcome outcome =
        registrar.register(ACCOUNT_ID, "key-1", SelfServiceRequestType.EXPORT);

    assertThat(outcome).isEqualTo(SelfServiceRequestRegistrar.Outcome.REPLAY);
  }

  private static class InMemoryRepo implements SelfServiceRequestRepository {

    protected final List<SelfServiceRequest> records = new ArrayList<>();

    @Override
    public Optional<SelfServiceRequest> find(AccountId accountId, String idempotencyKey) {
      return records.stream()
          .filter(r -> r.accountId().equals(accountId) && r.idempotencyKey().equals(idempotencyKey))
          .findFirst();
    }

    @Override
    public void record(SelfServiceRequest request) {
      records.add(request);
    }
  }
}
