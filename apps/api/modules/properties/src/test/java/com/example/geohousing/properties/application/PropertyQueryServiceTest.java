package com.example.geohousing.properties.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.properties.domain.CreatorId;
import com.example.geohousing.properties.domain.Property;
import com.example.geohousing.properties.domain.PropertyId;
import com.example.geohousing.properties.domain.PropertyNotFoundException;
import com.example.geohousing.properties.domain.PropertyType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PropertyQueryServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-20T10:00:00Z"), ZoneOffset.UTC);

  @Test
  void returnsThePropertyWhenItExists() {
    Property property = property("Vake Tower");
    PropertyQueryService service = new PropertyQueryService(new StoredProperties(property));

    assertThat(service.getById(property.id())).isSameAs(property);
  }

  @Test
  void theAdminReadReturnsAHiddenProperty() {
    Property property = property("Vake Tower");
    property.hide(CLOCK);
    PropertyQueryService service = new PropertyQueryService(new StoredProperties(property));

    assertThat(service.getById(property.id())).isSameAs(property);
  }

  @Test
  void thePublicReadReportsAHiddenPropertyAsMissing() {
    Property property = property("Vake Tower");
    property.hide(CLOCK);
    PropertyQueryService service = new PropertyQueryService(new StoredProperties(property));

    assertThatThrownBy(() -> service.getPublicById(property.id()))
        .isInstanceOf(PropertyNotFoundException.class);
  }

  @Test
  void thePublicReadStillReturnsAnActiveProperty() {
    Property property = property("Vake Tower");
    property.activate(CLOCK);
    PropertyQueryService service = new PropertyQueryService(new StoredProperties(property));

    assertThat(service.getPublicById(property.id())).isSameAs(property);
  }

  @Test
  void thePublicReadReturnsAMergedPropertyAsATombstone() {
    Property property = property("Vake Tower");
    property.mergeInto(PropertyId.of(UUID.randomUUID()), CLOCK);
    PropertyQueryService service = new PropertyQueryService(new StoredProperties(property));

    assertThat(service.getPublicById(property.id())).isSameAs(property);
  }

  @Test
  void thePublicReadReportsAnUnknownPropertyAsMissing() {
    PropertyQueryService service = new PropertyQueryService(new StoredProperties());

    assertThatThrownBy(() -> service.getPublicById(PropertyId.of(UUID.randomUUID())))
        .isInstanceOf(PropertyNotFoundException.class);
  }

  @Test
  void thePublicListKeepsVisiblePropertiesAndWithholdsHiddenAndMergedOnes() {
    Property draft = property("Draft Tower");
    Property active = property("Active Tower");
    active.activate(CLOCK);
    Property hidden = property("Hidden Tower");
    hidden.hide(CLOCK);
    Property merged = property("Merged Tower");
    merged.mergeInto(PropertyId.of(UUID.randomUUID()), CLOCK);
    PropertyQueryService service =
        new PropertyQueryService(new StoredProperties(draft, active, hidden, merged));

    assertThat(service.listRecentPublic(null))
        .extracting(Property::id)
        .containsExactly(draft.id(), active.id());
  }

  @Test
  void clampsTheRequestedListLimit() {
    StoredProperties repository = new StoredProperties();
    PropertyQueryService service = new PropertyQueryService(repository);

    service.listRecentPublic(null);
    assertThat(repository.lastRequestedLimit).isEqualTo(PropertyQueryService.DEFAULT_LIMIT);

    service.listRecentPublic(10_000);
    assertThat(repository.lastRequestedLimit).isEqualTo(PropertyQueryService.MAX_LIMIT);

    service.listRecentPublic(0);
    assertThat(repository.lastRequestedLimit).isEqualTo(1);

    service.listRecentPublic(5);
    assertThat(repository.lastRequestedLimit).isEqualTo(5);
  }

  private static Property property(String canonicalName) {
    return Property.create(
        PropertyId.of(UUID.randomUUID()),
        PropertyType.BUILDING,
        canonicalName,
        CreatorId.of(UUID.randomUUID()),
        CLOCK);
  }

  private static final class StoredProperties implements PropertyRepository {

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

    private final List<Property> properties;
    private Integer lastRequestedLimit;

    private StoredProperties(Property... properties) {
      this.properties = List.of(properties);
    }

    @Override
    public Optional<Property> findById(PropertyId propertyId) {
      return properties.stream().filter(property -> property.id().equals(propertyId)).findFirst();
    }

    @Override
    public List<Property> findRecent(int limit) {
      lastRequestedLimit = limit;
      return properties.stream().limit(limit).toList();
    }

    @Override
    public void create(Property property) {
      throw new UnsupportedOperationException("not needed for query tests");
    }
  }
}
