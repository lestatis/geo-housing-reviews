package com.example.geohousing.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.geohousing.identity.domain.Account;
import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.AccountRole;
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

class AccountDataExportServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-15T10:00:00Z"), ZoneOffset.UTC);
  private static final AccountId ACCOUNT_ID = AccountId.of(UUID.randomUUID());

  @Test
  void returnsTheUsersOwnAccountAndProfileIncludingEmailAndRecordsAnExport() {
    Account account = Account.provision(ACCOUNT_ID, "hashed-subject", "person@example.com", CLOCK);
    PublicProfile profile =
        PublicProfile.createDefault(ACCOUNT_ID, Pseudonym.of("Reviewer-a1b2"), "ka", CLOCK);
    InMemorySelfService selfService = new InMemorySelfService();
    AccountDataExportService service =
        new AccountDataExportService(
            new SingleAccount(account),
            new SingleProfile(profile),
            new SelfServiceRequestRegistrar(selfService, CLOCK));

    AccountExport export = service.export(ACCOUNT_ID, "key-1");

    assertThat(export.account().accountId()).isEqualTo(ACCOUNT_ID.value().toString());
    assertThat(export.account().email()).isEqualTo("person@example.com");
    assertThat(export.account().status()).isEqualTo("ACTIVE");
    assertThat(export.profile().pseudonym()).isEqualTo("Reviewer-a1b2");
    assertThat(export.profile().locale()).isEqualTo("ka");
    assertThat(selfService.records).hasSize(1);
    assertThat(selfService.records.get(0).type()).isEqualTo(SelfServiceRequestType.EXPORT);
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
    /**
     * Nothing to lock: one thread, one map. What the lock defends against is two transactions
     * reading the administrator set at once, which only a real database can stage — see {@code
     * LastAdministratorConcurrencyIntegrationTest}.
     */
    @Override
    public void lockActiveAdministrators() {
      // Deliberately empty.
    }

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

    @Override
    public Account save(Account account, long expectedVersion) {
      throw new UnsupportedOperationException("this double is not used for account writes");
    }

    @Override
    public long countByRole(AccountRole role) {
      throw new UnsupportedOperationException("this double is not used for role counting");
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

    @Override
    public java.util.Optional<PublicProfile> findByPseudonym(Pseudonym pseudonym) {
      throw new UnsupportedOperationException("this double is not used for pseudonym lookup");
    }
  }
}
