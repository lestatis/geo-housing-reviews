package com.example.geohousing.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A recorded identity-local data-subject request (export or deletion), keyed by a client-supplied
 * idempotency key scoped to the account. Retrying with the same key is safe; reusing a key for a
 * different {@link SelfServiceRequestType} is a client error (see {@code
 * IdempotencyKeyConflictException}).
 */
public final class SelfServiceRequest {

  private final UUID id;
  private final AccountId accountId;
  private final String idempotencyKey;
  private final SelfServiceRequestType type;
  private final Instant createdAt;

  private SelfServiceRequest(
      UUID id,
      AccountId accountId,
      String idempotencyKey,
      SelfServiceRequestType type,
      Instant createdAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.accountId = Objects.requireNonNull(accountId, "accountId");
    this.idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
    this.type = Objects.requireNonNull(type, "type");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
  }

  /** Creates a new request record with a generated id. */
  public static SelfServiceRequest create(
      AccountId accountId, String idempotencyKey, SelfServiceRequestType type, Instant createdAt) {
    return new SelfServiceRequest(UUID.randomUUID(), accountId, idempotencyKey, type, createdAt);
  }

  /** Rebuilds a request from persisted state. Intended for persistence adapters only. */
  public static SelfServiceRequest reconstitute(
      UUID id,
      AccountId accountId,
      String idempotencyKey,
      SelfServiceRequestType type,
      Instant createdAt) {
    return new SelfServiceRequest(id, accountId, idempotencyKey, type, createdAt);
  }

  public UUID id() {
    return id;
  }

  public AccountId accountId() {
    return accountId;
  }

  public String idempotencyKey() {
    return idempotencyKey;
  }

  public SelfServiceRequestType type() {
    return type;
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
