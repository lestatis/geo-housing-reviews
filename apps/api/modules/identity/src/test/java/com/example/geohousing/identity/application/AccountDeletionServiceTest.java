package com.example.geohousing.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.geohousing.identity.domain.Account;
import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.Pseudonym;
import com.example.geohousing.identity.domain.PublicProfile;
import com.example.geohousing.identity.domain.SelfServiceRequest;
import com.example.geohousing.identity.domain.SelfServiceRequestType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountDeletionServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-15T10:00:00Z"), ZoneOffset.UTC);
  private static final AccountId ACCOUNT_ID = AccountId.of(UUID.randomUUID());

  @Test
  void closesTheAccountAnonymizesTheProfileAndRecordsTheRequest() {
    Fixtures f = new Fixtures();

    Account result = f.service().delete(ACCOUNT_ID, "key-1");

    assertThat(result.isClosed()).isTrue();
    assertThat(result.email()).isEmpty();
    assertThat(f.deletion.account.isClosed()).isTrue();
    assertThat(f.deletion.profile.pseudonym().value()).startsWith("del-");
    assertThat(f.deletion.profile.avatarUrl()).isEmpty();
    assertThat(f.selfService.records).hasSize(1);
    assertThat(f.selfService.records.get(0).type()).isEqualTo(SelfServiceRequestType.DELETE);
  }

  @Test
  void isIdempotentWhenCalledAgainWithTheSameKey() {
    Fixtures f = new Fixtures();
    f.service().delete(ACCOUNT_ID, "key-1");

    Account result = f.service().delete(ACCOUNT_ID, "key-1");

    assertThat(result.isClosed()).isTrue();
    assertThat(f.selfService.records).hasSize(1); // replay, not a second record
  }

  private static final class Fixtures {
    final Account account =
        Account.provision(ACCOUNT_ID, "hashed-subject", "person@example.com", CLOCK);
    final PublicProfile profile =
        PublicProfile.createDefault(ACCOUNT_ID, Pseudonym.of("Reviewer-a1b2"), "en", CLOCK);
    final RecordingDeletion deletion = new RecordingDeletion();
    final InMemorySelfService selfService = new InMemorySelfService();

    AccountDeletionService service() {
      return new AccountDeletionService(
          new SingleAccount(account),
          new SingleProfile(profile),
          deletion,
          new SelfServiceRequestRegistrar(selfService, CLOCK),
          CLOCK);
    }
  }

  private static final class RecordingDeletion implements AccountDeletionRepository {
    private Account account;
    private PublicProfile profile;

    @Override
    public void applyDeletion(Account closedAccount, PublicProfile anonymizedProfile) {
      this.account = closedAccount;
      this.profile = anonymizedProfile;
    }
  }

  private static final class InMemorySelfService implements SelfServiceRequestRepository {
    private final List<SelfServiceRequest> records = new ArrayList<>();

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

  private static final class SingleAccount implements AccountRepository {
    private final Account account;

    private SingleAccount(Account account) {
      this.account = account;
    }

    @Override
    public Optional<Account> findById(AccountId accountId) {
      return account.id().equals(accountId) ? Optional.of(account) : Optional.empty();
    }

    @Override
    public Optional<Account> findByAuthSubjectHash(String authSubjectHash) {
      return Optional.empty();
    }
  }

  private static final class SingleProfile implements PublicProfileRepository {
    private final PublicProfile profile;

    private SingleProfile(PublicProfile profile) {
      this.profile = profile;
    }

    @Override
    public Optional<PublicProfile> findByAccountId(AccountId accountId) {
      return profile.accountId().equals(accountId) ? Optional.of(profile) : Optional.empty();
    }

    @Override
    public boolean isPseudonymInUse(Pseudonym pseudonym) {
      return false;
    }

    @Override
    public PublicProfile save(PublicProfile profile, long expectedVersion) {
      return profile;
    }
  }
}
