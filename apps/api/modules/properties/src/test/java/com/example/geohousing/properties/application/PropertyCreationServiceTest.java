package com.example.geohousing.properties.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.geohousing.properties.domain.Address;
import com.example.geohousing.properties.domain.Coordinates;
import com.example.geohousing.properties.domain.CreatorId;
import com.example.geohousing.properties.domain.Property;
import com.example.geohousing.properties.domain.PropertyId;
import com.example.geohousing.properties.domain.PropertyStatus;
import com.example.geohousing.properties.domain.PropertyType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PropertyCreationServiceTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-20T10:00:00Z"), ZoneOffset.UTC);
  private static final CreatorId CREATOR = CreatorId.of(UUID.randomUUID());

  @Test
  void createsADraftWhenNoDuplicatesAreFound() {
    InMemoryPropertyRepository repo = new InMemoryPropertyRepository();
    RecordingFinder finder = new RecordingFinder(List.of());
    PropertyCreationService service = new PropertyCreationService(repo, finder, CLOCK);
    Address address = new Address("GE", "Tbilisi", "Vake", "Chavchavadze", "37", "37 Chavchavadze");

    PropertyCreationResult result =
        service.create(
            new CreatePropertyCommand(
                PropertyType.BUILDING,
                "Vake Tower",
                CREATOR,
                address,
                Coordinates.of(41.71, 44.79),
                false));

    assertThat(result).isInstanceOf(PropertyCreationResult.Created.class);
    Property created = ((PropertyCreationResult.Created) result).property();
    assertThat(created.status()).isEqualTo(PropertyStatus.DRAFT);
    assertThat(created.address()).contains(address);
    assertThat(created.coordinates()).contains(Coordinates.of(41.71, 44.79));
    assertThat(repo.byId).containsKey(created.id());
    // The finder was consulted with the submitted details.
    assertThat(finder.lastName.get()).isEqualTo("Vake Tower");
    assertThat(finder.lastAddress.get()).isEqualTo(address);
  }

  @Test
  void returnsDuplicateCandidatesWithoutCreatingWhenNotAllowed() {
    InMemoryPropertyRepository repo = new InMemoryPropertyRepository();
    DuplicateCandidate candidate =
        new DuplicateCandidate(PropertyId.of(UUID.randomUUID()), "Vake Tower");
    PropertyCreationService service =
        new PropertyCreationService(repo, new RecordingFinder(List.of(candidate)), CLOCK);

    PropertyCreationResult result =
        service.create(
            new CreatePropertyCommand(
                PropertyType.BUILDING, "Vake Tower", CREATOR, null, null, false));

    assertThat(result).isInstanceOf(PropertyCreationResult.DuplicatesFound.class);
    assertThat(((PropertyCreationResult.DuplicatesFound) result).candidates())
        .containsExactly(candidate);
    assertThat(repo.byId).isEmpty();
  }

  @Test
  void createsAnywayWhenDuplicatesAreAllowed() {
    InMemoryPropertyRepository repo = new InMemoryPropertyRepository();
    RecordingFinder finder =
        new RecordingFinder(
            List.of(new DuplicateCandidate(PropertyId.of(UUID.randomUUID()), "Vake Tower")));
    PropertyCreationService service = new PropertyCreationService(repo, finder, CLOCK);

    PropertyCreationResult result =
        service.create(
            new CreatePropertyCommand(
                PropertyType.BUILDING, "Vake Tower", CREATOR, null, null, true));

    assertThat(result).isInstanceOf(PropertyCreationResult.Created.class);
    assertThat(repo.byId).hasSize(1);
    // With allowDuplicate=true the finder is not consulted at all.
    assertThat(finder.lastName.get()).isNull();
  }

  private static final class RecordingFinder implements DuplicateCandidateFinder {
    private final List<DuplicateCandidate> candidates;
    private final AtomicReference<String> lastName = new AtomicReference<>();
    private final AtomicReference<Address> lastAddress = new AtomicReference<>();

    private RecordingFinder(List<DuplicateCandidate> candidates) {
      this.candidates = candidates;
    }

    @Override
    public List<DuplicateCandidate> findCandidates(
        String canonicalName, Address address, Coordinates coordinates) {
      lastName.set(canonicalName);
      lastAddress.set(address);
      return candidates;
    }
  }

  private static final class InMemoryPropertyRepository implements PropertyRepository {

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
