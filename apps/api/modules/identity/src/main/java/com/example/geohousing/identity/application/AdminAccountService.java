package com.example.geohousing.identity.application;

import com.example.geohousing.identity.domain.Account;
import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.AdminAuditEvent;
import com.example.geohousing.identity.domain.AdminAuditOutcome;
import com.example.geohousing.identity.domain.Pseudonym;
import com.example.geohousing.identity.domain.PublicProfile;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Admin read access to accounts, audited on every call (SECURITY_PRIVACY.md: RBAC plus an
 * append-only audit log). Role enforcement is the security layer's job; this service assumes the
 * caller is already an authenticated admin and records who looked at which account.
 */
public final class AdminAccountService {

  private final AccountRepository accountRepository;
  private final PublicProfileRepository publicProfileRepository;
  private final AdminAuditEventRepository adminAuditEventRepository;
  private final Clock clock;

  public AdminAccountService(
      AccountRepository accountRepository,
      PublicProfileRepository publicProfileRepository,
      AdminAuditEventRepository adminAuditEventRepository,
      Clock clock) {
    this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository");
    this.publicProfileRepository =
        Objects.requireNonNull(publicProfileRepository, "publicProfileRepository");
    this.adminAuditEventRepository =
        Objects.requireNonNull(adminAuditEventRepository, "adminAuditEventRepository");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * Finds the account behind a public pseudonym, and records the access.
   *
   * <p>An administrator looking at a reported review knows the pseudonym and nothing else. Audited
   * exactly like a lookup by id, and for the same reason — this is the step that turns a public
   * name into a private account, and it is the one worth being able to review afterwards.
   */
  public Optional<Account> viewAccountByPseudonym(AccountId adminAccountId, Pseudonym pseudonym) {
    Objects.requireNonNull(adminAccountId, "adminAccountId");
    Objects.requireNonNull(pseudonym, "pseudonym");

    Optional<Account> target =
        publicProfileRepository
            .findByPseudonym(pseudonym)
            .map(PublicProfile::accountId)
            .flatMap(accountRepository::findById);
    // Records the account actually reached, or the caller themselves when nothing matched: the log
    // must not carry an id that was never looked up, and there is no target id to carry when the
    // pseudonym belongs to nobody.
    adminAuditEventRepository.record(
        AdminAuditEvent.accountView(
            UUID.randomUUID(),
            adminAccountId,
            target.map(Account::id).orElse(adminAccountId),
            target.isPresent() ? AdminAuditOutcome.FOUND : AdminAuditOutcome.NOT_FOUND,
            clock.instant()));
    return target;
  }

  /**
   * Looks up a target account and records the access. Returns empty rather than throwing when the
   * target is absent, so the audit write is always on the normal path and commits; the caller maps
   * empty to a 404. If the audit write fails it propagates — an admin never reads account data
   * without the access being recorded.
   */
  public Optional<Account> viewAccount(AccountId adminAccountId, AccountId targetAccountId) {
    Objects.requireNonNull(adminAccountId, "adminAccountId");
    Objects.requireNonNull(targetAccountId, "targetAccountId");

    Optional<Account> target = accountRepository.findById(targetAccountId);
    AdminAuditOutcome outcome =
        target.isPresent() ? AdminAuditOutcome.FOUND : AdminAuditOutcome.NOT_FOUND;
    adminAuditEventRepository.record(
        AdminAuditEvent.accountView(
            UUID.randomUUID(), adminAccountId, targetAccountId, outcome, clock.instant()));
    return target;
  }
}
