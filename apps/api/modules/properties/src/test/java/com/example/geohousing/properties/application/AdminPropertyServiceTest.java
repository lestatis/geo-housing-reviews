package com.example.geohousing.properties.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.properties.domain.AdminId;
import com.example.geohousing.properties.domain.CreatorId;
import com.example.geohousing.properties.domain.IllegalPropertyStateTransitionException;
import com.example.geohousing.properties.domain.Property;
import com.example.geohousing.properties.domain.PropertyAdminAction;
import com.example.geohousing.properties.domain.PropertyAdminAuditEvent;
import com.example.geohousing.properties.domain.PropertyAdminOutcome;
import com.example.geohousing.properties.domain.PropertyId;
import com.example.geohousing.properties.domain.PropertyStatus;
import com.example.geohousing.properties.domain.PropertyType;
import com.example.geohousing.properties.domain.PropertyVersionConflictException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminPropertyServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-20T10:00:00Z"), ZoneOffset.UTC);
  private static final AdminId ADMIN = AdminId.of(UUID.randomUUID());

  @Test
  void activatesAndRecordsAnAppliedAuditEvent() {
    Fixture fixture = new Fixture(draft());

    Optional<Property> result = fixture.service().activate(ADMIN, fixture.property.id(), 0L);

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().status()).isEqualTo(PropertyStatus.ACTIVE);
    assertThat(fixture.admin.applied).hasSize(1);
    PropertyAdminAuditEvent event = fixture.admin.applied.get(0);
    assertThat(event.action()).isEqualTo(PropertyAdminAction.ACTIVATE);
    assertThat(event.outcome()).isEqualTo(PropertyAdminOutcome.APPLIED);
    assertThat(event.adminId()).isEqualTo(ADMIN);
    assertThat(event.propertyId()).isEqualTo(fixture.property.id());
  }

  @Test
  void hidesAndRecordsTheAction() {
    Fixture fixture = new Fixture(draft());

    Optional<Property> result = fixture.service().hide(ADMIN, fixture.property.id(), 0L);

    assertThat(result.orElseThrow().status()).isEqualTo(PropertyStatus.HIDDEN);
    assertThat(fixture.admin.applied.get(0).action()).isEqualTo(PropertyAdminAction.HIDE);
  }

  @Test
  void mergesAndRecordsTheTarget() {
    Fixture fixture = new Fixture(draft());
    PropertyId target = PropertyId.of(UUID.randomUUID());

    Optional<Property> result = fixture.service().merge(ADMIN, fixture.property.id(), target, 0L);

    assertThat(result.orElseThrow().status()).isEqualTo(PropertyStatus.MERGED);
    assertThat(result.orElseThrow().mergedIntoPropertyId()).contains(target);
    assertThat(fixture.admin.applied.get(0).targetPropertyId()).contains(target);
  }

  @Test
  void recordsANotFoundAttemptAndReturnsEmptyForAnUnknownProperty() {
    Fixture fixture = new Fixture(null);
    PropertyId missing = PropertyId.of(UUID.randomUUID());

    Optional<Property> result = fixture.service().activate(ADMIN, missing, 0L);

    assertThat(result).isEmpty();
    assertThat(fixture.admin.applied).isEmpty();
    assertThat(fixture.admin.attempts).hasSize(1);
    assertThat(fixture.admin.attempts.get(0).outcome()).isEqualTo(PropertyAdminOutcome.NOT_FOUND);
    assertThat(fixture.admin.attempts.get(0).propertyId()).isEqualTo(missing);
  }

  @Test
  void propagatesAVersionConflict() {
    Fixture fixture = new Fixture(draft());
    fixture.admin.failWithVersionConflict = true;

    assertThatThrownBy(() -> fixture.service().activate(ADMIN, fixture.property.id(), 99L))
        .isInstanceOf(PropertyVersionConflictException.class);
  }

  @Test
  void refusesToActOnAMergedProperty() {
    Property merged = draft();
    merged.mergeInto(PropertyId.of(UUID.randomUUID()), CLOCK);
    Fixture fixture = new Fixture(merged);

    assertThatThrownBy(() -> fixture.service().activate(ADMIN, merged.id(), 0L))
        .isInstanceOf(IllegalPropertyStateTransitionException.class);
    assertThat(fixture.admin.applied).isEmpty();
  }

  private static Property draft() {
    return Property.create(
        PropertyId.of(UUID.randomUUID()),
        PropertyType.BUILDING,
        "Admin Tower",
        CreatorId.of(UUID.randomUUID()),
        CLOCK);
  }

  private static final class Fixture {
    private final Property property;
    private final RecordingAdminRepository admin = new RecordingAdminRepository();

    private Fixture(Property property) {
      this.property = property;
    }

    AdminPropertyService service() {
      return new AdminPropertyService(new SingleProperty(property), admin, CLOCK);
    }
  }

  private static final class RecordingAdminRepository implements PropertyAdminRepository {
    private final List<PropertyAdminAuditEvent> applied = new ArrayList<>();
    private final List<PropertyAdminAuditEvent> attempts = new ArrayList<>();
    private boolean failWithVersionConflict;

    @Override
    public void applyLifecycleChange(
        Property property, long expectedVersion, PropertyAdminAuditEvent event) {
      if (failWithVersionConflict) {
        throw new PropertyVersionConflictException("stale");
      }
      applied.add(event);
    }

    @Override
    public void recordAttempt(PropertyAdminAuditEvent event) {
      attempts.add(event);
    }
  }

  private static final class SingleProperty implements PropertyRepository {

    @Override
    public java.util.List<PropertyMatch> search(
        String text,
        com.example.geohousing.properties.domain.Coordinates point,
        double radiusMeters,
        int limit) {
      // Search is proven against a real PostgreSQL (trigram scoring and PostGIS distance are the
      // database's, not this fake's); these use-case tests do not exercise it.
      throw new UnsupportedOperationException("search is covered by PropertySearchIntegrationTest");
    }

    private final Property property;

    private SingleProperty(Property property) {
      this.property = property;
    }

    @Override
    public Optional<Property> findById(PropertyId propertyId) {
      return property != null && property.id().equals(propertyId)
          ? Optional.of(property)
          : Optional.empty();
    }

    @Override
    public List<Property> findRecent(int limit) {
      return property == null ? List.of() : List.of(property);
    }

    @Override
    public void create(Property property) {
      throw new UnsupportedOperationException("not needed for admin tests");
    }
  }
}
