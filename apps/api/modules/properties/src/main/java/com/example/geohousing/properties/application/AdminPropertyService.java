package com.example.geohousing.properties.application;

import com.example.geohousing.properties.domain.AdminId;
import com.example.geohousing.properties.domain.Property;
import com.example.geohousing.properties.domain.PropertyAdminAction;
import com.example.geohousing.properties.domain.PropertyAdminAuditEvent;
import com.example.geohousing.properties.domain.PropertyId;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Admin lifecycle actions on a property: activate, hide, merge. Authorization is the security
 * layer's job ({@code /api/admin/**} requires ROLE_ADMIN); this service applies the domain
 * transition and records the audit event alongside it.
 *
 * <p>A missing property returns {@link Optional#empty()} rather than throwing, so the {@code
 * NOT_FOUND} audit row is written on the committing path; the caller turns empty into a 404. A
 * version mismatch or an illegal transition propagates and nothing is written.
 */
public final class AdminPropertyService {

  private final PropertyRepository propertyRepository;
  private final PropertyAdminRepository propertyAdminRepository;
  private final Clock clock;

  public AdminPropertyService(
      PropertyRepository propertyRepository,
      PropertyAdminRepository propertyAdminRepository,
      Clock clock) {
    this.propertyRepository = Objects.requireNonNull(propertyRepository, "propertyRepository");
    this.propertyAdminRepository =
        Objects.requireNonNull(propertyAdminRepository, "propertyAdminRepository");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public Optional<Property> activate(AdminId adminId, PropertyId propertyId, long expectedVersion) {
    return apply(
        adminId,
        PropertyAdminAction.ACTIVATE,
        propertyId,
        null,
        expectedVersion,
        (property, now) -> property.activate(clock));
  }

  public Optional<Property> hide(AdminId adminId, PropertyId propertyId, long expectedVersion) {
    return apply(
        adminId,
        PropertyAdminAction.HIDE,
        propertyId,
        null,
        expectedVersion,
        (property, now) -> property.hide(clock));
  }

  public Optional<Property> merge(
      AdminId adminId, PropertyId propertyId, PropertyId targetPropertyId, long expectedVersion) {
    Objects.requireNonNull(targetPropertyId, "targetPropertyId");
    return apply(
        adminId,
        PropertyAdminAction.MERGE,
        propertyId,
        targetPropertyId,
        expectedVersion,
        (property, now) -> property.mergeInto(targetPropertyId, clock));
  }

  private Optional<Property> apply(
      AdminId adminId,
      PropertyAdminAction action,
      PropertyId propertyId,
      PropertyId targetPropertyId,
      long expectedVersion,
      BiConsumer<Property, Instant> transition) {
    Objects.requireNonNull(adminId, "adminId");
    Objects.requireNonNull(propertyId, "propertyId");
    Instant now = clock.instant();

    Optional<Property> found = propertyRepository.findById(propertyId);
    if (found.isEmpty()) {
      propertyAdminRepository.recordAttempt(
          PropertyAdminAuditEvent.notFound(adminId, action, propertyId, now));
      return Optional.empty();
    }

    Property property = found.get();
    transition.accept(property, now);
    propertyAdminRepository.applyLifecycleChange(
        property,
        expectedVersion,
        PropertyAdminAuditEvent.applied(adminId, action, propertyId, targetPropertyId, now));
    return Optional.of(property);
  }
}
