package com.example.geohousing.moderation.domain;

/**
 * Why a moderation decision was taken, as a short stable code.
 *
 * <p>MODERATION.md allows no unexplained moderation action, so this is required on every decision.
 * It is a free code rather than a closed enum on purpose: the reason taxonomy grows with policy,
 * and pinning it in a type (or a database CHECK) would mean a migration for every new reason —
 * which is how a moderator ends up choosing the nearest wrong code instead of the right one.
 */
public record ReasonCode(String value) {

  /** Matches the {@code varchar(64)} the schema stores. */
  public static final int MAX_LENGTH = 64;

  public ReasonCode {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("every moderation decision requires a reason code");
    }
    value = value.trim();
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "reason code must not exceed " + MAX_LENGTH + " characters");
    }
  }

  public static ReasonCode of(String value) {
    return new ReasonCode(value);
  }
}
