package com.example.geohousing.identity.domain;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Public projection of an account: pseudonym, optional avatar and locale. Deliberately holds no
 * email or legal identity (see {@code docs/DOMAIN_MODEL.md} PublicProfile).
 */
public final class PublicProfile {

  private static final String TOMBSTONE_PREFIX = "del-";

  /**
   * base36 chars needed to hold the full 128-bit account id (36^25 > 2^128 > 36^24). Fixed-width so
   * the encoding is injective; {@code "del-"} (4) + 25 = 29 stays within {@link
   * Pseudonym#MAX_LENGTH}.
   */
  private static final int TOMBSTONE_ID_WIDTH = 25;

  private final AccountId accountId;
  private Pseudonym pseudonym;
  private String avatarUrl;
  private String locale;
  private final Instant createdAt;
  private Instant updatedAt;
  private final long version;

  private PublicProfile(
      AccountId accountId,
      Pseudonym pseudonym,
      String avatarUrl,
      String locale,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    this.accountId = Objects.requireNonNull(accountId, "accountId");
    this.pseudonym = Objects.requireNonNull(pseudonym, "pseudonym");
    this.avatarUrl = avatarUrl;
    this.locale = requireText(locale, "locale");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    this.version = version;
  }

  /** Creates a fresh profile with the given (already allocated) default pseudonym. */
  public static PublicProfile createDefault(
      AccountId accountId, Pseudonym pseudonym, String locale, Clock clock) {
    Objects.requireNonNull(clock, "clock");
    Instant now = clock.instant();
    return new PublicProfile(accountId, pseudonym, null, locale, now, now, 0L);
  }

  /** Rebuilds a profile from persisted state. Intended for persistence adapters only. */
  public static PublicProfile reconstitute(
      AccountId accountId,
      Pseudonym pseudonym,
      String avatarUrl,
      String locale,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    return new PublicProfile(
        accountId, pseudonym, avatarUrl, locale, createdAt, updatedAt, version);
  }

  /** Applies a user-initiated profile edit. Uniqueness of the pseudonym is the caller's concern. */
  public void updateProfile(
      Pseudonym newPseudonym, String newAvatarUrl, String newLocale, Clock clock) {
    Objects.requireNonNull(clock, "clock");
    this.pseudonym = Objects.requireNonNull(newPseudonym, "newPseudonym");
    this.avatarUrl = newAvatarUrl;
    this.locale = requireText(newLocale, "locale");
    this.updatedAt = clock.instant();
  }

  /**
   * Replaces the pseudonym with a per-account tombstone and drops the avatar, so the profile no
   * longer carries anything user-identifying after account deletion. The tombstone is derived from
   * the account id so it stays unique (the pseudonym column is unique).
   */
  public void anonymize(Clock clock) {
    Objects.requireNonNull(clock, "clock");
    this.pseudonym = new Pseudonym(tombstoneFor(accountId));
    this.avatarUrl = null;
    this.updatedAt = clock.instant();
  }

  private static String tombstoneFor(AccountId accountId) {
    UUID uuid = accountId.value();
    byte[] bytes =
        ByteBuffer.allocate(16)
            .putLong(uuid.getMostSignificantBits())
            .putLong(uuid.getLeastSignificantBits())
            .array();
    String encoded = new BigInteger(1, bytes).toString(36);
    String padded = "0".repeat(TOMBSTONE_ID_WIDTH - encoded.length()) + encoded;
    return TOMBSTONE_PREFIX + padded;
  }

  public AccountId accountId() {
    return accountId;
  }

  public Pseudonym pseudonym() {
    return pseudonym;
  }

  public Optional<String> avatarUrl() {
    return Optional.ofNullable(avatarUrl);
  }

  public String locale() {
    return locale;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public long version() {
    return version;
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value;
  }
}
