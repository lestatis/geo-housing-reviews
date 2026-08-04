package com.example.geohousing.properties.infrastructure.persistence;

import com.example.geohousing.properties.domain.PropertyAdminAction;
import com.example.geohousing.properties.domain.PropertyAdminOutcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "properties", name = "property_admin_audit_event")
class PropertyAdminAuditEventJpaEntity {

  @Id private UUID id;

  @Column(name = "admin_account_id", nullable = false)
  private UUID adminAccountId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private PropertyAdminAction action;

  @Column(name = "property_id")
  private UUID propertyId;

  @Column(name = "target_property_id")
  private UUID targetPropertyId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PropertyAdminOutcome outcome;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected PropertyAdminAuditEventJpaEntity() {}

  PropertyAdminAuditEventJpaEntity(
      UUID id,
      UUID adminAccountId,
      PropertyAdminAction action,
      UUID propertyId,
      UUID targetPropertyId,
      PropertyAdminOutcome outcome,
      Instant createdAt) {
    this.id = id;
    this.adminAccountId = adminAccountId;
    this.action = action;
    this.propertyId = propertyId;
    this.targetPropertyId = targetPropertyId;
    this.outcome = outcome;
    this.createdAt = createdAt;
  }

  UUID adminAccountId() {
    return adminAccountId;
  }

  PropertyAdminAction action() {
    return action;
  }

  UUID propertyId() {
    return propertyId;
  }

  UUID targetPropertyId() {
    return targetPropertyId;
  }

  PropertyAdminOutcome outcome() {
    return outcome;
  }

  Instant createdAt() {
    return createdAt;
  }
}
