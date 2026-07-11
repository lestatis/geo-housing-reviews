package com.example.geohousing.identity.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Private authentication identity and lifecycle status for a user (see {@code
 * docs/DOMAIN_MODEL.md}). Holds the mapping to the external auth subject and the server-side
 * authorization role. The public-facing name lives separately in {@link PublicProfile} and is never
 * exposed from here.
 */
public final class Account {

  private final AccountId id;
  private final String authSubject;
  private String email;
  private final AccountRole role;
  private AccountStatus status;
  private final Instant createdAt;
  private Instant closedAt;
  private final long version;

  private Account(
      AccountId id,
      String authSubject,
      String email,
      AccountRole role,
      AccountStatus status,
      Instant createdAt,
      Instant closedAt,
      long version) {
    this.id = Objects.requireNonNull(id, "id");
    this.authSubject = requireText(authSubject, "authSubject");
    this.email = email;
    this.role = Objects.requireNonNull(role, "role");
    this.status = Objects.requireNonNull(status, "status");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.closedAt = closedAt;
    this.version = version;
  }

  /** Creates a new active account with the default {@link AccountRole#USER} role. */
  public static Account provision(AccountId id, String authSubject, String email, Clock clock) {
    Objects.requireNonNull(clock, "clock");
    return new Account(
        id, authSubject, email, AccountRole.USER, AccountStatus.ACTIVE, clock.instant(), null, 0L);
  }

  /** Rebuilds an account from persisted state. Intended for persistence adapters only. */
  public static Account reconstitute(
      AccountId id,
      String authSubject,
      String email,
      AccountRole role,
      AccountStatus status,
      Instant createdAt,
      Instant closedAt,
      long version) {
    return new Account(id, authSubject, email, role, status, createdAt, closedAt, version);
  }

  /**
   * Closes the account permanently and scrubs the email (a piece of confidential personal data that
   * should not survive closure). Idempotent: closing an already-closed account is a no-op.
   */
  public void close(Clock clock) {
    Objects.requireNonNull(clock, "clock");
    if (status == AccountStatus.CLOSED) {
      return;
    }
    this.status = AccountStatus.CLOSED;
    this.closedAt = clock.instant();
    this.email = null;
  }

  public boolean isClosed() {
    return status == AccountStatus.CLOSED;
  }

  public AccountId id() {
    return id;
  }

  public String authSubject() {
    return authSubject;
  }

  public Optional<String> email() {
    return Optional.ofNullable(email);
  }

  public AccountRole role() {
    return role;
  }

  public AccountStatus status() {
    return status;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Optional<Instant> closedAt() {
    return Optional.ofNullable(closedAt);
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
