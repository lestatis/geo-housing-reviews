package com.example.geohousing.properties.application;

import com.example.geohousing.properties.domain.Property;
import com.example.geohousing.properties.domain.PropertyId;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Creates a new {@code DRAFT} property, surfacing possible duplicates first. On a plain attempt
 * (allowDuplicate=false), if the {@link DuplicateCandidateFinder} finds candidates the property is
 * <em>not</em> created and the candidates are returned so the user can pick an existing one; the
 * caller re-submits with allowDuplicate=true to create anyway.
 */
public final class PropertyCreationService {

  private final PropertyRepository propertyRepository;
  private final DuplicateCandidateFinder duplicateCandidateFinder;
  private final Clock clock;

  public PropertyCreationService(
      PropertyRepository propertyRepository,
      DuplicateCandidateFinder duplicateCandidateFinder,
      Clock clock) {
    this.propertyRepository = Objects.requireNonNull(propertyRepository, "propertyRepository");
    this.duplicateCandidateFinder =
        Objects.requireNonNull(duplicateCandidateFinder, "duplicateCandidateFinder");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public PropertyCreationResult create(CreatePropertyCommand command) {
    Objects.requireNonNull(command, "command");

    if (!command.allowDuplicate()) {
      List<DuplicateCandidate> candidates =
          duplicateCandidateFinder.findCandidates(
              command.canonicalName(), command.address(), command.coordinates());
      if (!candidates.isEmpty()) {
        return new PropertyCreationResult.DuplicatesFound(candidates);
      }
    }

    Property property =
        Property.create(
            PropertyId.of(UUID.randomUUID()),
            command.type(),
            command.canonicalName(),
            command.createdBy(),
            clock);
    if (command.address() != null) {
      property.setAddress(command.address(), clock);
    }
    if (command.coordinates() != null) {
      property.setCoordinates(command.coordinates(), clock);
    }
    propertyRepository.create(property);
    return new PropertyCreationResult.Created(property);
  }
}
