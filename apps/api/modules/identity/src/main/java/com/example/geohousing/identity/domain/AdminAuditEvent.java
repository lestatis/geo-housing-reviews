package com.example.geohousing.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * An append-only record that an admin performed an action against an account (SECURITY_PRIVACY.md).
 * A missing {@code targetAccountId} is allowed for future actions that do not target a single
 * account; the only action modelled now ({@link AdminAuditAction#VIEW_ACCOUNT}) always sets it.
 */
public final class AdminAuditEvent {

  private final UUID id;
  private final AccountId adminAccountId;
  private final AdminAuditAction action;
  private final AccountId targetAccountId;
  private final AdminAuditOutcome outcome;
  private final Instant occurredAt;

  private AdminAuditEvent(
      UUID id,
      AccountId adminAccountId,
      AdminAuditAction action,
      AccountId targetAccountId,
      AdminAuditOutcome outcome,
      Instant occurredAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.adminAccountId = Objects.requireNonNull(adminAccountId, "adminAccountId");
    this.action = Objects.requireNonNull(action, "action");
    this.targetAccountId = targetAccountId;
    this.outcome = Objects.requireNonNull(outcome, "outcome");
    this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
  }

  /** Records that an admin viewed (or attempted to view) a target account. */
  public static AdminAuditEvent accountView(
      UUID id,
      AccountId adminAccountId,
      AccountId targetAccountId,
      AdminAuditOutcome outcome,
      Instant occurredAt) {
    return new AdminAuditEvent(
        id,
        adminAccountId,
        AdminAuditAction.VIEW_ACCOUNT,
        Objects.requireNonNull(targetAccountId, "targetAccountId"),
        outcome,
        occurredAt);
  }

  /**
   * Records that an admin changed (or tried to change) another account's role.
   *
   * <p>The action names the direction rather than the resulting role, so the log reads as a history
   * of grants and removals rather than a list of states to diff.
   */
  public static AdminAuditEvent roleChange(
      UUID id,
      AccountId adminAccountId,
      AccountId targetAccountId,
      AccountRole newRole,
      AdminAuditOutcome outcome,
      Instant occurredAt) {
    return new AdminAuditEvent(
        id,
        adminAccountId,
        newRole == AccountRole.ADMIN ? AdminAuditAction.GRANT_ADMIN : AdminAuditAction.REVOKE_ADMIN,
        Objects.requireNonNull(targetAccountId, "targetAccountId"),
        outcome,
        occurredAt);
  }

  /** Records that an admin placed, or tried to place or lift, a restriction on an account. */
  public static AdminAuditEvent restriction(
      UUID id,
      AccountId adminAccountId,
      AccountId targetAccountId,
      AdminAuditAction action,
      AdminAuditOutcome outcome,
      Instant occurredAt) {
    return new AdminAuditEvent(
        id,
        adminAccountId,
        Objects.requireNonNull(action, "action"),
        Objects.requireNonNull(targetAccountId, "targetAccountId"),
        outcome,
        occurredAt);
  }

  /** Rebuilds an event from persisted state. Intended for persistence adapters only. */
  public static AdminAuditEvent reconstitute(
      UUID id,
      AccountId adminAccountId,
      AdminAuditAction action,
      AccountId targetAccountId,
      AdminAuditOutcome outcome,
      Instant occurredAt) {
    return new AdminAuditEvent(id, adminAccountId, action, targetAccountId, outcome, occurredAt);
  }

  public UUID id() {
    return id;
  }

  public AccountId adminAccountId() {
    return adminAccountId;
  }

  public AdminAuditAction action() {
    return action;
  }

  public Optional<AccountId> targetAccountId() {
    return Optional.ofNullable(targetAccountId);
  }

  public AdminAuditOutcome outcome() {
    return outcome;
  }

  public Instant occurredAt() {
    return occurredAt;
  }
}
