package com.example.geohousing.properties.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.properties.api.PropertySummary;
import com.example.geohousing.properties.api.PropertyVisibility;
import com.example.geohousing.properties.api.UnresolvableMergeChainException;
import com.example.geohousing.properties.domain.CreatorId;
import com.example.geohousing.properties.domain.Property;
import com.example.geohousing.properties.domain.PropertyId;
import com.example.geohousing.properties.domain.PropertyType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PropertyCatalogServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-20T10:00:00Z"), ZoneOffset.UTC);
  private static final CreatorId CREATOR = CreatorId.of(UUID.randomUUID());

  private final InMemoryPropertyRepository repository = new InMemoryPropertyRepository();
  private final PropertyCatalogService catalog = new PropertyCatalogService(repository);

  private Property store(String name) {
    Property property =
        Property.create(
            PropertyId.of(UUID.randomUUID()), PropertyType.BUILDING, name, CREATOR, CLOCK);
    repository.create(property);
    return property;
  }

  @Test
  void reportsAnOrdinaryPropertyAsPublic() {
    Property property = store("Vake Tower");

    PropertySummary summary = catalog.findSurviving(property.id().value()).orElseThrow();

    assertThat(summary.propertyId()).isEqualTo(property.id().value());
    assertThat(summary.canonicalName()).isEqualTo("Vake Tower");
    assertThat(summary.visibility()).isEqualTo(PropertyVisibility.PUBLIC);
    assertThat(summary.isPublic()).isTrue();
  }

  @Test
  void aPropertyAnAdministratorHidIsWithheldRatherThanMissing() {
    Property property = store("Vake Tower");
    property.activate(CLOCK);
    property.hide(CLOCK);

    PropertySummary summary = catalog.findSurviving(property.id().value()).orElseThrow();

    assertThat(summary.visibility()).isEqualTo(PropertyVisibility.WITHHELD);
    assertThat(summary.isPublic()).isFalse();
  }

  @Test
  void aMergedPropertyResolvesToTheOneThatSurvived() {
    Property survivor = store("Vake Tower");
    Property duplicate = store("vake tower (dup)");
    duplicate.mergeInto(survivor.id(), CLOCK);

    PropertySummary summary = catalog.findSurviving(duplicate.id().value()).orElseThrow();

    assertThat(summary.propertyId()).isEqualTo(survivor.id().value());
    assertThat(summary.canonicalName()).isEqualTo("Vake Tower");
  }

  @Test
  void followsAChainOfMergesToItsEnd() {
    Property survivor = store("Vake Tower");
    Property middle = store("Vake Tower II");
    Property first = store("vake tower");
    middle.mergeInto(survivor.id(), CLOCK);
    first.mergeInto(middle.id(), CLOCK);

    assertThat(catalog.findSurviving(first.id().value()).orElseThrow().propertyId())
        .isEqualTo(survivor.id().value());
  }

  @Test
  void anUnknownPropertyIsSimplyAbsent() {
    assertThat(catalog.findSurviving(UUID.randomUUID())).isEmpty();
  }

  @Test
  void aMergePointingAtAPropertyThatIsNotThereReadsAsAbsent() {
    Property orphan = store("Vake Tower");
    orphan.mergeInto(PropertyId.of(UUID.randomUUID()), CLOCK);

    assertThat(catalog.findSurviving(orphan.id().value())).isEmpty();
  }

  @Test
  void aMergeCycleFailsLoudlyInsteadOfLoopingOrSilentlyDroppingTheProperty() {
    // Nothing in the aggregate stops an administrator merging A into B and later B into A;
    // until that is guarded on the admin path, the resolver must not spin.
    Property first = store("Vake Tower");
    Property second = store("vake tower");
    first.mergeInto(second.id(), CLOCK);
    second.mergeInto(first.id(), CLOCK);

    assertThatThrownBy(() -> catalog.findSurviving(first.id().value()))
        .isInstanceOf(UnresolvableMergeChainException.class);
  }

  private static final class InMemoryPropertyRepository implements PropertyRepository {
    private final Map<PropertyId, Property> byId = new HashMap<>();

    @Override
    public Optional<Property> findById(PropertyId propertyId) {
      return Optional.ofNullable(byId.get(propertyId));
    }

    @Override
    public List<Property> findRecent(int limit) {
      return byId.values().stream().limit(limit).toList();
    }

    @Override
    public void create(Property property) {
      byId.put(property.id(), property);
    }
  }
}
