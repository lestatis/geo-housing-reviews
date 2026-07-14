package com.example.geohousing.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.identity.domain.Account;
import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.AccountNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminAccountLookupServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-14T10:00:00Z"), ZoneOffset.UTC);

  @Test
  void returnsAccountWhenItExists() {
    Account account =
        Account.provision(
            AccountId.of(UUID.randomUUID()), "hashed-subject", "person@example.com", CLOCK);
    AdminAccountLookupService service =
        new AdminAccountLookupService(new SingleAccountRepository(account));

    assertThat(service.findAccount(account.id())).isSameAs(account);
  }

  @Test
  void rejectsUnknownAccount() {
    AdminAccountLookupService service =
        new AdminAccountLookupService(new SingleAccountRepository(null));

    assertThatThrownBy(() -> service.findAccount(AccountId.of(UUID.randomUUID())))
        .isInstanceOf(AccountNotFoundException.class);
  }

  private static final class SingleAccountRepository implements AccountRepository {

    private final Account account;

    private SingleAccountRepository(Account account) {
      this.account = account;
    }

    @Override
    public Optional<Account> findById(AccountId accountId) {
      return account != null && account.id().equals(accountId)
          ? Optional.of(account)
          : Optional.empty();
    }

    @Override
    public Optional<Account> findByAuthSubjectHash(String authSubjectHash) {
      return account != null && account.authSubjectHash().equals(authSubjectHash)
          ? Optional.of(account)
          : Optional.empty();
    }
  }
}
