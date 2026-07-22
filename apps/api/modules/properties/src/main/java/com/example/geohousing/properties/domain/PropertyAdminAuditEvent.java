package com.example.geohousing.properties.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * An append-only record that an admin performed a lifecycle action on a property. The target
 * property is present only for a merge; the property id is recorded even when no such property
 * exists, so a failed attempt is still auditable.
 */
public final class PropertyAdminAuditEvent {

  private final UUID id;
  private final AdminId adminId;
  private final PropertyAdminAction action;
  private final PropertyId propertyId;
  private final PropertyId targetPropertyId;
  private final PropertyAdminOutcome outcome;
  private final Instant occurredAt;

  private PropertyAdminAuditEvent(
      UUID id,
      AdminId adminId,
      PropertyAdminAction action,
      PropertyId propertyId,
      PropertyId targetPropertyId,
      PropertyAdminOutcome outcome,
      Instant occurredAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.adminId = Objects.requireNonNull(adminId, "adminId");
    this.action = Objects.requireNonNull(action, "action");
    this.propertyId = Objects.requireNonNull(propertyId, "propertyId");
    this.targetPropertyId = targetPropertyId;
    this.outcome = Objects.requireNonNull(outcome, "outcome");
    this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    if (action == PropertyAdminAction.MERGE
        && outcome == PropertyAdminOutcome.APPLIED
        && targetPropertyId == null) {
      throw new IllegalArgumentException("an applied merge must record its target property");
    }
  }

  /** Records an action that took effect. */
  public static PropertyAdminAuditEvent applied(
      AdminId adminId,
      PropertyAdminAction action,
      PropertyId propertyId,
      PropertyId targetPropertyId,
      Instant occurredAt) {
    return new PropertyAdminAuditEvent(
        UUID.randomUUID(),
        adminId,
        action,
        propertyId,
        targetPropertyId,
        PropertyAdminOutcome.APPLIED,
        occurredAt);
  }

  /** Records an action attempted against a property that does not exist. */
  public static PropertyAdminAuditEvent notFound(
      AdminId adminId, PropertyAdminAction action, PropertyId propertyId, Instant occurredAt) {
    return new PropertyAdminAuditEvent(
        UUID.randomUUID(),
        adminId,
        action,
        propertyId,
        null,
        PropertyAdminOutcome.NOT_FOUND,
        occurredAt);
  }

  public UUID id() {
    return id;
  }

  public AdminId adminId() {
    return adminId;
  }

  public PropertyAdminAction action() {
    return action;
  }

  public PropertyId propertyId() {
    return propertyId;
  }

  public Optional<PropertyId> targetPropertyId() {
    return Optional.ofNullable(targetPropertyId);
  }

  public PropertyAdminOutcome outcome() {
    return outcome;
  }

  public Instant occurredAt() {
    return occurredAt;
  }
}
