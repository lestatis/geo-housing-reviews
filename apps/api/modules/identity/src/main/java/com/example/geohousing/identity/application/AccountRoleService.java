package com.example.geohousing.identity.application;

import com.example.geohousing.identity.domain.Account;
import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.AccountNotFoundException;
import com.example.geohousing.identity.domain.AccountRole;
import com.example.geohousing.identity.domain.AdminAuditEvent;
import com.example.geohousing.identity.domain.AdminAuditOutcome;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Granting and removing administrative access.
 *
 * <p>Until this existed, every administrator was made by editing {@code identity.account} directly
 * — which is why both test suites still do it to create the first one. Role enforcement is the
 * security layer's job; this service assumes the caller is already an authenticated administrator
 * and decides whether the change is one the platform can survive.
 *
 * <p>One rule lives here rather than on {@link Account}, because it is about the population of
 * accounts rather than any one of them: the last administrator may not be demoted.
 *
 * <p>There is deliberately no rule against acting on yourself. Stepping down is legitimate while
 * somebody else still holds the role, and the last-administrator check refuses it when it is not.
 * Self-promotion needs no rule of its own: only an administrator can reach this service, so raising
 * your own role means a role you already hold, and {@link Account#changeRole} refuses that. An
 * earlier draft did forbid self-changes, and it made the last-administrator rule unreachable — you
 * can only demote somebody else, and doing so always leaves you.
 *
 * <p>Every outcome is recorded, refusals included. The audit log is the only record of how an
 * account became privileged, so the write is on the normal path and a failure to record propagates
 * — a grant that could not be audited must not be a grant that happened.
 */
public final class AccountRoleService {

  private final AccountRepository accountRepository;
  private final AdminAuditEventRepository adminAuditEventRepository;
  private final Clock clock;

  public AccountRoleService(
      AccountRepository accountRepository,
      AdminAuditEventRepository adminAuditEventRepository,
      Clock clock) {
    this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository");
    this.adminAuditEventRepository =
        Objects.requireNonNull(adminAuditEventRepository, "adminAuditEventRepository");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * Changes a target account's role, recording the attempt either way.
   *
   * @throws AccountNotFoundException if no such account exists
   * @throws LastAdministratorException if this would leave the platform with no administrator
   * @throws com.example.geohousing.identity.domain.AccountClosedException if the target is closed
   * @throws com.example.geohousing.identity.domain.OptimisticLockConflictException if it changed
   *     under the request
   */
  public Account changeRole(
      AccountId adminAccountId,
      AccountId targetAccountId,
      AccountRole newRole,
      long expectedVersion) {
    Objects.requireNonNull(adminAccountId, "adminAccountId");
    Objects.requireNonNull(targetAccountId, "targetAccountId");
    Objects.requireNonNull(newRole, "newRole");

    Optional<Account> found = accountRepository.findById(targetAccountId);
    if (found.isEmpty()) {
      record(adminAccountId, targetAccountId, newRole, AdminAuditOutcome.NOT_FOUND);
      throw new AccountNotFoundException(targetAccountId);
    }
    Account target = found.get();

    if (wouldRemoveTheLastAdministrator(target, newRole)) {
      record(adminAccountId, targetAccountId, newRole, AdminAuditOutcome.REFUSED);
      throw new LastAdministratorException(
          "this is the only administrator; grant the role to someone else first");
    }

    try {
      target.changeRole(newRole, clock);
    } catch (RuntimeException refused) {
      // A closed account, or a role it already holds. Recorded for the same reason as the rest.
      record(adminAccountId, targetAccountId, newRole, AdminAuditOutcome.REFUSED);
      throw refused;
    }

    // Recorded before success is returned: if the audit write fails the caller sees a failure, and
    // the surrounding transaction rolls the role change back with it.
    Account saved = accountRepository.save(target, expectedVersion);
    record(adminAccountId, targetAccountId, newRole, AdminAuditOutcome.APPLIED);
    return saved;
  }

  private boolean wouldRemoveTheLastAdministrator(Account target, AccountRole newRole) {
    return target.role() == AccountRole.ADMIN
        && newRole != AccountRole.ADMIN
        && accountRepository.countByRole(AccountRole.ADMIN) <= 1;
  }

  private void record(
      AccountId adminAccountId,
      AccountId targetAccountId,
      AccountRole newRole,
      AdminAuditOutcome outcome) {
    adminAuditEventRepository.record(
        AdminAuditEvent.roleChange(
            UUID.randomUUID(), adminAccountId, targetAccountId, newRole, outcome, clock.instant()));
  }
}
