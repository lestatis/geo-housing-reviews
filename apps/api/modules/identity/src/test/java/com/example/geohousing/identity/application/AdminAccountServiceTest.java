package com.example.geohousing.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.identity.domain.Account;
import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.AccountRole;
import com.example.geohousing.identity.domain.AdminAuditAction;
import com.example.geohousing.identity.domain.AdminAuditEvent;
import com.example.geohousing.identity.domain.AdminAuditOutcome;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminAccountServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-15T10:00:00Z"), ZoneOffset.UTC);
  private static final AccountId ADMIN_ID = AccountId.of(UUID.randomUUID());

  @Test
  void returnsAccountAndRecordsAFoundAuditEvent() {
    Account target =
        Account.provision(
            AccountId.of(UUID.randomUUID()), "hashed-subject", "person@example.com", CLOCK);
    RecordingAuditRepository audit = new RecordingAuditRepository();
    AdminAccountService service =
        new AdminAccountService(new SingleAccountRepository(target), audit, CLOCK);

    Optional<Account> result = service.viewAccount(ADMIN_ID, target.id());

    assertThat(result).containsSame(target);
    assertThat(audit.events).hasSize(1);
    AdminAuditEvent event = audit.events.get(0);
    assertThat(event.action()).isEqualTo(AdminAuditAction.VIEW_ACCOUNT);
    assertThat(event.outcome()).isEqualTo(AdminAuditOutcome.FOUND);
    assertThat(event.adminAccountId()).isEqualTo(ADMIN_ID);
    assertThat(event.targetAccountId()).contains(target.id());
    assertThat(event.occurredAt()).isEqualTo(CLOCK.instant());
  }

  @Test
  void returnsEmptyAndRecordsANotFoundAuditEventForAnUnknownAccount() {
    RecordingAuditRepository audit = new RecordingAuditRepository();
    AdminAccountService service =
        new AdminAccountService(new SingleAccountRepository(null), audit, CLOCK);
    AccountId missing = AccountId.of(UUID.randomUUID());

    Optional<Account> result = service.viewAccount(ADMIN_ID, missing);

    assertThat(result).isEmpty();
    assertThat(audit.events).hasSize(1);
    AdminAuditEvent event = audit.events.get(0);
    assertThat(event.outcome()).isEqualTo(AdminAuditOutcome.NOT_FOUND);
    assertThat(event.targetAccountId()).contains(missing);
  }

  @Test
  void propagatesAnAuditFailureSoNoUnauditedAccessOccurs() {
    Account target =
        Account.provision(AccountId.of(UUID.randomUUID()), "hashed-subject", null, CLOCK);
    AdminAuditEventRepository failing =
        event -> {
          throw new IllegalStateException("audit store unavailable");
        };
    AdminAccountService service =
        new AdminAccountService(new SingleAccountRepository(target), failing, CLOCK);

    assertThatThrownBy(() -> service.viewAccount(ADMIN_ID, target.id()))
        .isInstanceOf(IllegalStateException.class);
  }

  private static final class RecordingAuditRepository implements AdminAuditEventRepository {

    private final List<AdminAuditEvent> events = new ArrayList<>();

    @Override
    public void record(AdminAuditEvent event) {
      events.add(event);
    }
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

    @Override
    public Account save(Account account, long expectedVersion) {
      throw new UnsupportedOperationException("this double is not used for account writes");
    }

    @Override
    public long countByRole(AccountRole role) {
      throw new UnsupportedOperationException("this double is not used for role counting");
    }
  }
}
