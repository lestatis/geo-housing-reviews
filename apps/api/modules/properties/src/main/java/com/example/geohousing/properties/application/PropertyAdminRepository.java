package com.example.geohousing.properties.application;

import com.example.geohousing.properties.domain.Property;
import com.example.geohousing.properties.domain.PropertyAdminAuditEvent;

/**
 * Persistence boundary for admin lifecycle actions. The mutation and its audit row are written
 * together so an admin change can never be applied without being recorded — the application layer
 * stays framework-free, so the atomicity lives in the adapter rather than a transactional service.
 */
public interface PropertyAdminRepository {

  /**
   * Persists the property's lifecycle fields (status, merge target, updated-at) and the audit event
   * in one transaction.
   *
   * @throws com.example.geohousing.properties.domain.PropertyVersionConflictException if the stored
   *     version no longer matches {@code expectedVersion}
   */
  void applyLifecycleChange(Property property, long expectedVersion, PropertyAdminAuditEvent event);

  /** Records an action attempted against a property that does not exist. */
  void recordAttempt(PropertyAdminAuditEvent event);
}
