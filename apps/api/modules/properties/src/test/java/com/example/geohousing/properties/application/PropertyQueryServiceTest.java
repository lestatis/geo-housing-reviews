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
    Property property =
        Property.create(
            PropertyId.of(UUID.randomUUID()),
            PropertyType.BUILDING,
            "Vake Tower",
            CreatorId.of(UUID.randomUUID()),
            CLOCK);
    PropertyQueryService service = new PropertyQueryService(new SingleProperty(property));

    assertThat(service.getById(property.id())).isSameAs(property);
  }

  @Test
  void throwsWhenThePropertyIsMissing() {
    PropertyQueryService service = new PropertyQueryService(new SingleProperty(null));

    assertThatThrownBy(() -> service.getById(PropertyId.of(UUID.randomUUID())))
        .isInstanceOf(PropertyNotFoundException.class);
  }

  @Test
  void clampsTheRequestedListLimit() {
    SingleProperty repository = new SingleProperty(null);
    PropertyQueryService service = new PropertyQueryService(repository);

    service.listRecent(null);
    assertThat(repository.lastRequestedLimit).isEqualTo(PropertyQueryService.DEFAULT_LIMIT);

    service.listRecent(10_000);
    assertThat(repository.lastRequestedLimit).isEqualTo(PropertyQueryService.MAX_LIMIT);

    service.listRecent(0);
    assertThat(repository.lastRequestedLimit).isEqualTo(1);

    service.listRecent(5);
    assertThat(repository.lastRequestedLimit).isEqualTo(5);
  }

  private static final class SingleProperty implements PropertyRepository {
    private final Property property;
    private Integer lastRequestedLimit;

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
      lastRequestedLimit = limit;
      return property == null ? List.of() : List.of(property);
    }

    @Override
    public void create(Property property) {
      throw new UnsupportedOperationException("not needed for query tests");
    }
  }
}
