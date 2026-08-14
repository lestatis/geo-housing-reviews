package com.example.geohousing.identity.application;

import com.example.geohousing.identity.api.AccountRestrictionUseCase;
import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.AccountNotFoundException;
import com.example.geohousing.identity.domain.AdminAuditAction;
import com.example.geohousing.identity.domain.AdminAuditEvent;
import com.example.geohousing.identity.domain.AdminAuditOutcome;
import com.example.geohousing.identity.domain.RestrictionScope;
import com.example.geohousing.identity.domain.UserRestriction;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Placing and lifting the restrictions that stop an account contributing.
 *
 * <p>MODERATION.md lists "restrict account" among the actions a moderator may take, and until now
 * nothing could take it. Role enforcement is the security layer's job; this service assumes an
 * authenticated administrator and decides whether the restriction is coherent.
 *
 * <p>Every outcome is recorded, refusals included, and a lift is recorded as its own action rather
 * than as a variant of placing one — ending somebody else's restriction early is a distinct
 * decision, and a log that conflated them could not answer "who let this account back in?".
 */
public final class AccountRestrictionService implements AccountRestrictionUseCase {

  private final UserRestrictionRepository restrictions;
  private final AccountRepository accounts;
  private final AdminAuditEventRepository audit;
  private final Clock clock;

  public AccountRestrictionService(
      UserRestrictionRepository restrictions,
      AccountRepository accounts,
      AdminAuditEventRepository audit,
      Clock clock) {
    this.restrictions = Objects.requireNonNull(restrictions, "restrictions");
    this.accounts = Objects.requireNonNull(accounts, "accounts");
    this.audit = Objects.requireNonNull(audit, "audit");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * Restricts an account from now until {@code endAt}, or indefinitely when that is null.
   *
   * @throws AccountNotFoundException if no such account exists
   * @throws AlreadyRestrictedException if an active restriction already covers this scope
   */
  @Override
  public UserRestriction restrict(
      AccountId moderatorId,
      AccountId targetId,
      RestrictionScope scope,
      String reason,
      Instant endAt) {
    Objects.requireNonNull(moderatorId, "moderatorId");
    Objects.requireNonNull(targetId, "targetId");
    Objects.requireNonNull(scope, "scope");

    if (accounts.findById(targetId).isEmpty()) {
      record(moderatorId, targetId, AdminAuditAction.RESTRICT_ACCOUNT, AdminAuditOutcome.NOT_FOUND);
      throw new AccountNotFoundException(targetId);
    }

    // Locked before the question is asked, so the answer is still true when it is acted on. Two
    // moderators restricting the same account at the same moment both found nothing and both wrote
    // one, leaving overlapping restrictions the duration rules cannot describe.

    accounts.lockAccount(targetId);

    Instant now = clock.instant();
    if (hasActiveRestrictionInScope(targetId, scope, now)) {
      record(moderatorId, targetId, AdminAuditAction.RESTRICT_ACCOUNT, AdminAuditOutcome.REFUSED);
      throw new AlreadyRestrictedException(
          "an active restriction already covers this account in scope " + scope);
    }

    UserRestriction placed;
    try {
      placed =
          UserRestriction.place(
              UUID.randomUUID(), targetId, scope, reason, endAt, moderatorId, clock);
    } catch (RuntimeException refused) {
      // A blank reason, or an end before the start. Recorded like every other refusal.
      record(moderatorId, targetId, AdminAuditAction.RESTRICT_ACCOUNT, AdminAuditOutcome.REFUSED);
      throw refused;
    }

    restrictions.create(placed);
    record(moderatorId, targetId, AdminAuditAction.RESTRICT_ACCOUNT, AdminAuditOutcome.APPLIED);
    return placed;
  }

  /**
   * Ends a restriction now, keeping the record of it.
   *
   * <p>The restriction must belong to {@code accountId}. A lift is a moderation action on a named
   * person, and one addressed to A must never land on B because a stale or mistyped link put
   * somebody else's identifier in the path — a restriction lifted on the wrong account is silent,
   * and the moderator has no way to notice.
   *
   * @throws RestrictionNotFoundException if this account has no such restriction
   * @throws RestrictionNotActiveException if it has already ended
   */
  @Override
  public UserRestriction lift(AccountId moderatorId, AccountId accountId, UUID restrictionId) {
    Objects.requireNonNull(moderatorId, "moderatorId");
    Objects.requireNonNull(accountId, "accountId");
    Objects.requireNonNull(restrictionId, "restrictionId");

    Optional<UserRestriction> found =
        restrictions.findById(restrictionId).filter(it -> it.accountId().equals(accountId));
    if (found.isEmpty()) {
      // No target account to name, so the audit row records the attempt against nothing rather than
      // guessing at whose restriction it might have been.
      audit.recordRefusedAttempt(
          AdminAuditEvent.restriction(
              UUID.randomUUID(),
              moderatorId,
              moderatorId,
              AdminAuditAction.LIFT_RESTRICTION,
              AdminAuditOutcome.NOT_FOUND,
              clock.instant()));
      throw new RestrictionNotFoundException("no restriction for identifier " + restrictionId);
    }

    UserRestriction restriction = found.get();
    Instant now = clock.instant();
    if (!restriction.isActiveAt(now)) {
      record(
          moderatorId,
          restriction.accountId(),
          AdminAuditAction.LIFT_RESTRICTION,
          AdminAuditOutcome.REFUSED);
      throw new RestrictionNotActiveException("this restriction has already ended");
    }

    UserRestriction lifted = restriction.liftedAt(now);
    restrictions.save(lifted);
    record(
        moderatorId,
        restriction.accountId(),
        AdminAuditAction.LIFT_RESTRICTION,
        AdminAuditOutcome.APPLIED);
    return lifted;
  }

  /** Everything ever placed on this account, for a moderator judging a pattern. */
  @Override
  public List<UserRestriction> history(AccountId accountId) {
    return restrictions.findAllFor(Objects.requireNonNull(accountId, "accountId"));
  }

  private boolean hasActiveRestrictionInScope(
      AccountId accountId, RestrictionScope scope, Instant now) {
    return restrictions.findActiveRestrictions(accountId, now).stream()
        .anyMatch(restriction -> restriction.scope() == scope && restriction.isActiveAt(now));
  }

  private void record(
      AccountId moderatorId,
      AccountId targetId,
      AdminAuditAction action,
      AdminAuditOutcome outcome) {
    AdminAuditEvent event =
        AdminAuditEvent.restriction(
            UUID.randomUUID(), moderatorId, targetId, action, outcome, clock.instant());
    // The same split as role changes: an applied restriction and its record commit together, while
    // a refusal is about to throw and would otherwise roll away the only evidence that somebody
    // tried. Missed here when the role service learned it, one commit earlier.
    if (outcome == AdminAuditOutcome.APPLIED) {
      audit.record(event);
    } else {
      audit.recordRefusedAttempt(event);
    }
  }
}
