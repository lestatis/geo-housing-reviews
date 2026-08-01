package com.example.geohousing.identity.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Private authentication identity and lifecycle status for a user (see {@code
 * docs/DOMAIN_MODEL.md}). Holds a non-reversible hash of the external auth subject (never the raw
 * subject — see ADR-0006) and the server-side authorization role. The public-facing name lives
 * separately in {@link PublicProfile} and is never exposed from here.
 */
public final class Account {

  private final AccountId id;
  private final String authSubjectHash;
  private String email;
  private AccountRole role;
  private AccountStatus status;
  private final Instant createdAt;
  private Instant closedAt;
  private final long version;

  private Account(
      AccountId id,
      String authSubjectHash,
      String email,
      AccountRole role,
      AccountStatus status,
      Instant createdAt,
      Instant closedAt,
      long version) {
    this.id = Objects.requireNonNull(id, "id");
    this.authSubjectHash = requireText(authSubjectHash, "authSubjectHash");
    this.email = email;
    this.role = Objects.requireNonNull(role, "role");
    this.status = Objects.requireNonNull(status, "status");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.closedAt = closedAt;
    this.version = version;
  }

  /** Creates a new active account with the default {@link AccountRole#USER} role. */
  public static Account provision(AccountId id, String authSubjectHash, String email, Clock clock) {
    Objects.requireNonNull(clock, "clock");
    return new Account(
        id,
        authSubjectHash,
        email,
        AccountRole.USER,
        AccountStatus.ACTIVE,
        clock.instant(),
        null,
        0L);
  }

  /** Rebuilds an account from persisted state. Intended for persistence adapters only. */
  public static Account reconstitute(
      AccountId id,
      String authSubjectHash,
      String email,
      AccountRole role,
      AccountStatus status,
      Instant createdAt,
      Instant closedAt,
      long version) {
    return new Account(id, authSubjectHash, email, role, status, createdAt, closedAt, version);
  }

  /**
   * Closes the account permanently and scrubs the email (a piece of confidential personal data that
   * should not survive closure). The auth-subject hash is intentionally retained: it is already
   * non-reversible, so it carries no raw identifier, and keeping it lets provisioning reject any
   * future login by the same subject (no resurrection — see ADR-0006). Idempotent: closing an
   * already-closed account is a no-op.
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

  /**
   * Grants or removes administrative access.
   *
   * <p>Refuses a closed account: closure is permanent and its subject can never authenticate again
   * (no resurrection — ADR-0006), so a role on it would be privilege attached to nobody.
   *
   * <p>Refuses a change to the role already held. Every change is audited, and the audit log is the
   * only record of how an account became privileged; a no-op that still wrote a row would put a
   * grant in the log that granted nothing.
   *
   * <p>Who may call this — and the rules about the last administrator and about acting on oneself —
   * belong to the application service, because they are about the caller and the population of
   * accounts rather than about this one account.
   */
  public void changeRole(AccountRole newRole, Clock clock) {
    Objects.requireNonNull(newRole, "newRole");
    Objects.requireNonNull(clock, "clock");
    if (status == AccountStatus.CLOSED) {
      throw new AccountClosedException("a closed account cannot be given a role");
    }
    if (newRole == role) {
      throw new IllegalArgumentException("this account already holds the role " + newRole);
    }
    this.role = newRole;
  }

  public boolean isClosed() {
    return status == AccountStatus.CLOSED;
  }

  public AccountId id() {
    return id;
  }

  public String authSubjectHash() {
    return authSubjectHash;
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
