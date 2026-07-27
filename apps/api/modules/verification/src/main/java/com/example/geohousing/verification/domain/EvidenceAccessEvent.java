package com.example.geohousing.verification.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * An append-only record that an evidence object was read or deleted (TRUST_VERIFICATION.md §6:
 * "evidence access audit"; SECURITY_PRIVACY.md §4). Every moderator read leaves one of these, so
 * looking at someone's lease is never an untraceable act.
 *
 * <p>The accessor is absent only for a {@code DELETE} by the retention sweep, which no person
 * performs — the same shape the decision audit uses for a system expiry.
 */
public final class EvidenceAccessEvent {

  private final UUID id;
  private final EvidenceId evidenceId;
  private final UUID accessorAccountId;
  private final EvidenceAccessAction action;
  private final Instant occurredAt;

  private EvidenceAccessEvent(
      UUID id,
      EvidenceId evidenceId,
      UUID accessorAccountId,
      EvidenceAccessAction action,
      Instant occurredAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.evidenceId = Objects.requireNonNull(evidenceId, "evidenceId");
    this.action = Objects.requireNonNull(action, "action");
    this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    if (action == EvidenceAccessAction.READ && accessorAccountId == null) {
      throw new IllegalArgumentException("a read of evidence must record who read it");
    }
    this.accessorAccountId = accessorAccountId;
  }

  /** Records a moderator opening an evidence object. */
  public static EvidenceAccessEvent read(
      EvidenceId evidenceId, UUID accessorAccountId, Instant occurredAt) {
    return new EvidenceAccessEvent(
        UUID.randomUUID(),
        evidenceId,
        Objects.requireNonNull(accessorAccountId, "accessorAccountId"),
        EvidenceAccessAction.READ,
        occurredAt);
  }

  /** Records a deletion by the retention sweep, which has no human actor. */
  public static EvidenceAccessEvent systemDeletion(EvidenceId evidenceId, Instant occurredAt) {
    return new EvidenceAccessEvent(
        UUID.randomUUID(), evidenceId, null, EvidenceAccessAction.DELETE, occurredAt);
  }

  /** Records a deletion a person triggered — cancelling a case, or a moderator's decision. */
  public static EvidenceAccessEvent deletion(
      EvidenceId evidenceId, UUID accessorAccountId, Instant occurredAt) {
    return new EvidenceAccessEvent(
        UUID.randomUUID(),
        evidenceId,
        Objects.requireNonNull(accessorAccountId, "accessorAccountId"),
        EvidenceAccessAction.DELETE,
        occurredAt);
  }

  public UUID id() {
    return id;
  }

  public EvidenceId evidenceId() {
    return evidenceId;
  }

  public Optional<UUID> accessorAccountId() {
    return Optional.ofNullable(accessorAccountId);
  }

  public EvidenceAccessAction action() {
    return action;
  }

  public Instant occurredAt() {
    return occurredAt;
  }
}
