package com.example.geohousing.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A time-bounded restriction placed on an account. Whether a restriction is currently in force is
 * computed on demand via {@link #isActiveAt(Instant)} rather than denormalized onto the account, so
 * it can never go stale when the window ends.
 *
 * <p>Placing a restriction is a moderation concern (a future module); this type only models the
 * fact and its active-window check.
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

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value;
  }
}
