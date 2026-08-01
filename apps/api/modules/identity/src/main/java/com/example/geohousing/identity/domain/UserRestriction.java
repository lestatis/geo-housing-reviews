package com.example.geohousing.identity.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A time-bounded restriction placed on an account. Whether a restriction is currently in force is
 * computed on demand via {@link #isActiveAt(Instant)} rather than denormalized onto the account, so
 * it can never go stale when the window ends.
 *
 * <p>A restriction is always attributed and always explained: the row names the moderator who
 * placed it and the reason the affected account can be told, because a restriction nobody can be
 * asked about and nobody can correct is not something this platform should be able to create.
 */
public final class UserRestriction {

  private final UUID id;
  private final AccountId accountId;
  private final RestrictionScope scope;
  private final String reason;
  private final Instant startAt;
  private final Instant endAt;
  private final AccountId moderatorAccountId;
  private final AppealStatus appealStatus;
  private final Instant createdAt;

  private UserRestriction(
      UUID id,
      AccountId accountId,
      RestrictionScope scope,
      String reason,
      Instant startAt,
      Instant endAt,
      AccountId moderatorAccountId,
      AppealStatus appealStatus,
      Instant createdAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.accountId = Objects.requireNonNull(accountId, "accountId");
    this.scope = Objects.requireNonNull(scope, "scope");
    this.reason = requireText(reason, "reason");
    this.startAt = Objects.requireNonNull(startAt, "startAt");
    this.endAt = endAt;
    this.moderatorAccountId = moderatorAccountId;
    this.appealStatus = Objects.requireNonNull(appealStatus, "appealStatus");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    if (endAt != null && endAt.isBefore(startAt)) {
      throw new IllegalArgumentException("restriction endAt must not be before startAt");
    }
  }

  /**
   * Places a restriction on an account, starting now.
   *
   * <p>{@code endAt} may be null, meaning indefinite. Nothing has been appealed at this point, so
   * the appeal status starts at {@link AppealStatus#NONE} rather than being a caller's choice.
   */
  public static UserRestriction place(
      UUID id,
      AccountId accountId,
      RestrictionScope scope,
      String reason,
      Instant endAt,
      AccountId moderatorAccountId,
      Clock clock) {
    Objects.requireNonNull(clock, "clock");
    Objects.requireNonNull(moderatorAccountId, "moderatorAccountId");
    Instant now = clock.instant();
    return new UserRestriction(
        id, accountId, scope, reason, now, endAt, moderatorAccountId, AppealStatus.NONE, now);
  }

  /**
   * The same restriction, ended at {@code liftedAt}.
   *
   * <p>Lifting closes the window rather than removing the row. A lifted restriction is history — an
   * appeal, or a later moderator looking at a pattern, needs to see that it happened and when it
   * stopped.
   */
  public UserRestriction liftedAt(Instant liftedAt) {
    Objects.requireNonNull(liftedAt, "liftedAt");
    return new UserRestriction(
        id,
        accountId,
        scope,
        reason,
        startAt,
        liftedAt,
        moderatorAccountId,
        appealStatus,
        createdAt);
  }

  /** Rebuilds a restriction from persisted (or test-seeded) state. */
  public static UserRestriction reconstitute(
      UUID id,
      AccountId accountId,
      RestrictionScope scope,
      String reason,
      Instant startAt,
      Instant endAt,
      AccountId moderatorAccountId,
      AppealStatus appealStatus,
      Instant createdAt) {
    return new UserRestriction(
        id, accountId, scope, reason, startAt, endAt, moderatorAccountId, appealStatus, createdAt);
  }

  /**
   * True when {@code now} falls within the restriction window: on or after {@code startAt} and
   * strictly before {@code endAt} (an absent {@code endAt} means indefinite).
   */
  public boolean isActiveAt(Instant now) {
    Objects.requireNonNull(now, "now");
    if (now.isBefore(startAt)) {
      return false;
    }
    return endAt == null || now.isBefore(endAt);
  }

  public UUID id() {
    return id;
  }

  public AccountId accountId() {
    return accountId;
  }

  public RestrictionScope scope() {
    return scope;
  }

  public String reason() {
    return reason;
  }

  public Instant startAt() {
    return startAt;
  }

  public Optional<Instant> endAt() {
    return Optional.ofNullable(endAt);
  }

  public Optional<AccountId> moderatorAccountId() {
    return Optional.ofNullable(moderatorAccountId);
  }

  public AppealStatus appealStatus() {
    return appealStatus;
  }

  public Instant createdAt() {
    return createdAt;
  }

  /**
   * Trimmed as well as required. The schema's CHECK is {@code length(trim(reason)) > 0}, so the
   * database already treats surrounding space as absent; storing it anyway would only mean the
   * reason shown to an affected account has whitespace nobody typed on purpose.
   */
  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value.trim();
  }
}
