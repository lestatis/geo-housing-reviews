package com.example.geohousing.identity.application;

import com.example.geohousing.identity.domain.Account;
import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.AdminAuditEvent;
import com.example.geohousing.identity.domain.AdminAuditOutcome;
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
  private final AdminAuditEventRepository adminAuditEventRepository;
  private final Clock clock;

  public AdminAccountService(
      AccountRepository accountRepository,
      AdminAuditEventRepository adminAuditEventRepository,
      Clock clock) {
    this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository");
    this.adminAuditEventRepository =
        Objects.requireNonNull(adminAuditEventRepository, "adminAuditEventRepository");
    this.clock = Objects.requireNonNull(clock, "clock");
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
