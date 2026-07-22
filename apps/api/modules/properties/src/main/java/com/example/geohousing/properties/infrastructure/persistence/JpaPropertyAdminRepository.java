package com.example.geohousing.properties.infrastructure.persistence;

import com.example.geohousing.properties.application.PropertyAdminRepository;
import com.example.geohousing.properties.domain.Property;
import com.example.geohousing.properties.domain.PropertyAdminAuditEvent;
import com.example.geohousing.properties.domain.PropertyId;
import com.example.geohousing.properties.domain.PropertyNotFoundException;
import com.example.geohousing.properties.domain.PropertyVersionConflictException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes an admin lifecycle change and its audit row in one transaction, so a property can never be
 * mutated by an admin without the action being recorded.
 */
@Repository
public class JpaPropertyAdminRepository implements PropertyAdminRepository {

  private final SpringDataPropertyRepository properties;
  private final SpringDataPropertyAdminAuditEventRepository auditEvents;

  public JpaPropertyAdminRepository(
      SpringDataPropertyRepository properties,
      SpringDataPropertyAdminAuditEventRepository auditEvents) {
    this.properties = properties;
    this.auditEvents = auditEvents;
  }

  @Override
  @Transactional
  public void applyLifecycleChange(
      Property property, long expectedVersion, PropertyAdminAuditEvent event) {
    PropertyJpaEntity entity =
        properties
            .findById(property.id().value())
            .orElseThrow(() -> new PropertyNotFoundException(property.id()));

    if (entity.version() != expectedVersion) {
      throw new PropertyVersionConflictException("property version does not match");
    }

    entity.applyLifecycleChange(
        property.status(),
        property.mergedIntoPropertyId().map(PropertyId::value).orElse(null),
        property.updatedAt());

    try {
      properties.saveAndFlush(entity);
    } catch (ObjectOptimisticLockingFailureException exception) {
      throw new PropertyVersionConflictException("property version does not match");
    }
    auditEvents.saveAndFlush(PropertyAdminAuditEventJpaMapper.toEntity(event));
  }

  @Override
  @Transactional
  public void recordAttempt(PropertyAdminAuditEvent event) {
    auditEvents.saveAndFlush(PropertyAdminAuditEventJpaMapper.toEntity(event));
  }
}
