package com.example.geohousing.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.identity.domain.Account;
import com.example.geohousing.identity.domain.AccountClosedException;
import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.AccountNotFoundException;
import com.example.geohousing.identity.domain.AccountRole;
import com.example.geohousing.identity.domain.AdminAuditAction;
import com.example.geohousing.identity.domain.AdminAuditEvent;
import com.example.geohousing.identity.domain.AdminAuditOutcome;
import com.example.geohousing.identity.domain.OptimisticLockConflictException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountRoleServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-08-01T10:00:00Z"), ZoneOffset.UTC);

  private final InMemoryAccountRepository accounts = new InMemoryAccountRepository();
  private final RecordingAuditRepository audit = new RecordingAuditRepository();
  private final AccountRoleService service = new AccountRoleService(accounts, audit, CLOCK);

  @Test
  void anAdministratorCanGrantAdministrativeAccess() {
    Account admin = accounts.givenAdmin();
    Account resident = accounts.givenUser();

    Account promoted = service.changeRole(admin.id(), resident.id(), AccountRole.ADMIN, 0L);

    assertThat(promoted.role()).isEqualTo(AccountRole.ADMIN);
    assertThat(accounts.findById(resident.id()).orElseThrow().role()).isEqualTo(AccountRole.ADMIN);
    assertThat(audit.only().action()).isEqualTo(AdminAuditAction.GRANT_ADMIN);
    assertThat(audit.only().outcome()).isEqualTo(AdminAuditOutcome.APPLIED);
    assertThat(audit.only().adminAccountId()).isEqualTo(admin.id());
    assertThat(audit.only().targetAccountId()).contains(resident.id());
  }

  @Test
  void anAdministratorCanRemoveAnothersAdministrativeAccess() {
    Account admin = accounts.givenAdmin();
    Account other = accounts.givenAdmin();

    service.changeRole(admin.id(), other.id(), AccountRole.USER, 0L);

    assertThat(accounts.findById(other.id()).orElseThrow().role()).isEqualTo(AccountRole.USER);
    assertThat(audit.only().action()).isEqualTo(AdminAuditAction.REVOKE_ADMIN);
    assertThat(audit.only().outcome()).isEqualTo(AdminAuditOutcome.APPLIED);
  }

  @Test
  void anAdministratorMayStepDownWhileAnotherRemains() {
    // Acting on yourself is allowed on purpose. Forbidding it would make the rule below unreachable
    // — you can only demote somebody else, and doing so always leaves you holding the role.
    Account admin = accounts.givenAdmin();
    accounts.givenAdmin();

    service.changeRole(admin.id(), admin.id(), AccountRole.USER, 0L);

    assertThat(accounts.findById(admin.id()).orElseThrow().role()).isEqualTo(AccountRole.USER);
    assertThat(audit.only().outcome()).isEqualTo(AdminAuditOutcome.APPLIED);
  }

  @Test
  void raisingYourOwnRoleIsRefusedBecauseYouAlreadyHoldIt() {
    // Only an administrator reaches this service, so "promote myself" is always a role already
    // held — no separate rule needed, and none that could be forgotten.
    Account admin = accounts.givenAdmin();

    assertThatThrownBy(() -> service.changeRole(admin.id(), admin.id(), AccountRole.ADMIN, 0L))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(audit.only().outcome()).isEqualTo(AdminAuditOutcome.REFUSED);
  }

  @Test
  void theLastAdministratorCannotBeDemoted() {
    // One request would otherwise leave nobody able to moderate, verify, or grant the role back —
    // recoverable only by editing the database, which is the thing this endpoint exists to avoid.
    Account admin = accounts.givenAdmin();
    Account second = accounts.givenAdmin();

    // Two administrators, so one may step down and leave exactly one.
    service.changeRole(second.id(), second.id(), AccountRole.USER, 0L);
    audit.events.clear();

    assertThatThrownBy(() -> service.changeRole(admin.id(), admin.id(), AccountRole.USER, 0L))
        .isInstanceOf(LastAdministratorException.class);

    assertThat(accounts.findById(admin.id()).orElseThrow().role()).isEqualTo(AccountRole.ADMIN);
    assertThat(audit.only().outcome()).isEqualTo(AdminAuditOutcome.REFUSED);
  }

  @Test
  void demotingAnAdministratorIsAllowedWhileAnotherRemains() {
    Account admin = accounts.givenAdmin();
    Account second = accounts.givenAdmin();
    Account third = accounts.givenAdmin();

    service.changeRole(admin.id(), second.id(), AccountRole.USER, 0L);

    assertThat(accounts.findById(second.id()).orElseThrow().role()).isEqualTo(AccountRole.USER);
    assertThat(accounts.findById(third.id()).orElseThrow().role()).isEqualTo(AccountRole.ADMIN);
  }

  @Test
  void anUnknownAccountIsRecordedAsAnAttemptRatherThanIgnored() {
    Account admin = accounts.givenAdmin();
    AccountId missing = AccountId.of(UUID.randomUUID());

    assertThatThrownBy(() -> service.changeRole(admin.id(), missing, AccountRole.ADMIN, 0L))
        .isInstanceOf(AccountNotFoundException.class);

    assertThat(audit.only().outcome()).isEqualTo(AdminAuditOutcome.NOT_FOUND);
    assertThat(audit.only().targetAccountId()).contains(missing);
  }

  @Test
  void aClosedAccountCannotBeGivenARole() {
    Account admin = accounts.givenAdmin();
    Account closed = accounts.givenClosedUser();

    assertThatThrownBy(() -> service.changeRole(admin.id(), closed.id(), AccountRole.ADMIN, 0L))
        .isInstanceOf(AccountClosedException.class);

    assertThat(audit.only().outcome()).isEqualTo(AdminAuditOutcome.REFUSED);
  }

  @Test
  void aStaleVersionIsRefusedSoTwoAdministratorsDoNotOverwriteEachOther() {
    Account admin = accounts.givenAdmin();
    Account resident = accounts.givenUser();

    assertThatThrownBy(() -> service.changeRole(admin.id(), resident.id(), AccountRole.ADMIN, 7L))
        .isInstanceOf(OptimisticLockConflictException.class);

    assertThat(accounts.findById(resident.id()).orElseThrow().role()).isEqualTo(AccountRole.USER);
  }

  @Test
  void anAuditFailurePropagatesSoNoGrantIsReportedAsSucceeding() {
    // The audit log is the only record of how an account became privileged, so a grant that could
    // not be recorded must never be reported as done. That the account write is rolled back too is
    // the transaction's job, not this service's — covered where a real database is involved.
    Account admin = accounts.givenAdmin();
    Account resident = accounts.givenUser();
    AccountRoleService failing =
        new AccountRoleService(
            accounts,
            event -> {
              throw new IllegalStateException("audit store unavailable");
            },
            CLOCK);

    assertThatThrownBy(() -> failing.changeRole(admin.id(), resident.id(), AccountRole.ADMIN, 0L))
        .isInstanceOf(IllegalStateException.class);
  }

  private static final class RecordingAuditRepository implements AdminAuditEventRepository {

    private final List<AdminAuditEvent> events = new ArrayList<>();

    /**
     * Which door each event came through. The two are not interchangeable: one joins the caller's
     * transaction so a change and its record commit together, the other escapes it so a refusal
     * survives the rollback it is about to cause. A fake that recorded both the same way would let
     * the routing be swapped without any test noticing.
     */
    private final List<AdminAuditEvent> refusalsRecordedIndependently = new ArrayList<>();

    @Override
    public void record(AdminAuditEvent event) {
      events.add(event);
    }

    @Override
    public void recordRefusedAttempt(AdminAuditEvent event) {
      refusalsRecordedIndependently.add(event);
      events.add(event);
    }

    private AdminAuditEvent only() {
      assertThat(events).hasSize(1);
      return events.getFirst();
    }
  }

  /** An account store that behaves like the real one about versions, because that is under test. */
  private static final class InMemoryAccountRepository implements AccountRepository {

    /**
     * One thread and one map cannot stage a race — that is {@code
     * LastAdministratorConcurrencyIntegrationTest}'s job. What this fake can observe is whether the
     * lock was taken <em>before</em> the count, which is the ordering the rule depends on and the
     * part a unit test can hold onto.
     */
    private boolean lockedAdministrators;

    private boolean countedAdministratorsWithoutLocking;

    @Override
    public void lockActiveAdministrators() {
      lockedAdministrators = true;
    }

    private final Map<AccountId, Account> stored = new HashMap<>();
    private final Map<AccountId, Long> versions = new HashMap<>();

    private Account givenUser() {
      return store(
          Account.provision(
              AccountId.of(UUID.randomUUID()), UUID.randomUUID().toString(), null, CLOCK));
    }

    private Account givenAdmin() {
      Account account = givenUser();
      account.changeRole(AccountRole.ADMIN, CLOCK);
      return account;
    }

    private Account givenClosedUser() {
      Account account = givenUser();
      account.close(CLOCK);
      return account;
    }

    private Account store(Account account) {
      stored.put(account.id(), account);
      versions.put(account.id(), 0L);
      return account;
    }

    /**
     * Returns a fresh object each read, as the JPA adapter does when it maps a row. Handing back
     * the stored instance would let a mutation survive a failed save, which is exactly the thing
     * the version check exists to prevent.
     */
    @Override
    public Optional<Account> findById(AccountId accountId) {
      return Optional.ofNullable(stored.get(accountId)).map(InMemoryAccountRepository::copyOf);
    }

    private static Account copyOf(Account account) {
      return Account.reconstitute(
          account.id(),
          account.authSubjectHash(),
          account.email().orElse(null),
          account.role(),
          account.status(),
          account.createdAt(),
          account.closedAt().orElse(null),
          account.version());
    }

    @Override
    public Optional<Account> findByAuthSubjectHash(String authSubjectHash) {
      return stored.values().stream()
          .filter(account -> account.authSubjectHash().equals(authSubjectHash))
          .findFirst();
    }

    @Override
    public Account save(Account account, long expectedVersion) {
      long current = versions.getOrDefault(account.id(), -1L);
      if (current != expectedVersion) {
        throw new OptimisticLockConflictException(
            "account " + account.id().value() + " changed under this request");
      }
      versions.put(account.id(), current + 1);
      stored.put(account.id(), account);
      return account;
    }

    @Override
    public long countByRole(AccountRole role) {
      if (role == AccountRole.ADMIN && !lockedAdministrators) {
        countedAdministratorsWithoutLocking = true;
      }
      return stored.values().stream()
          .filter(account -> !account.isClosed() && account.role() == role)
          .count();
    }
  }

  @Test
  void theAdministratorsAreLockedBeforeTheyAreCounted() {
    // The rule is about a set, so the set has to be held still while it is judged. Counting first
    // and locking afterwards — or not locking at all — reads correctly and lets two simultaneous
    // demotions both pass. The database proves that in
    // LastAdministratorConcurrencyIntegrationTest; what belongs here is the ordering itself.
    Account admin = accounts.givenAdmin();
    Account other = accounts.givenAdmin();

    service.changeRole(admin.id(), other.id(), AccountRole.USER, 0L);

    assertThat(accounts.countedAdministratorsWithoutLocking)
        .as("the administrators were counted before anything stopped them changing")
        .isFalse();
  }

  @Test
  void aRefusalIsRecordedOutsideTheTransactionItIsAboutToRollBack() {
    // The refusal throws, and the throw rolls back everything the caller wrote — including, if this
    // routing were wrong, the only record that somebody tried to remove the last administrator.
    Account onlyAdmin = accounts.givenAdmin();

    assertThatThrownBy(
            () -> service.changeRole(onlyAdmin.id(), onlyAdmin.id(), AccountRole.USER, 0L))
        .isInstanceOf(LastAdministratorException.class);

    assertThat(audit.refusalsRecordedIndependently)
        .as("a refusal recorded in the doomed transaction leaves no trace of the attempt")
        .hasSize(1);
  }

  @Test
  void anAppliedChangeIsRecordedInsideTheTransactionItBelongsTo() {
    // The mirror image: a grant and its audit row must be one unit, so an audit failure takes the
    // grant with it rather than leaving it unrecorded.
    Account admin = accounts.givenAdmin();
    Account resident = accounts.givenUser();

    service.changeRole(admin.id(), resident.id(), AccountRole.ADMIN, 0L);

    assertThat(audit.refusalsRecordedIndependently)
        .as("a successful change escaped the transaction that should own it")
        .isEmpty();
  }
}
