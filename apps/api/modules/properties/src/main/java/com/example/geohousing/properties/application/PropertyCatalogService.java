package com.example.geohousing.properties.application;

import com.example.geohousing.properties.api.PropertyCatalog;
import com.example.geohousing.properties.api.PropertySummary;
import com.example.geohousing.properties.api.PropertyVisibility;
import com.example.geohousing.properties.api.UnresolvableMergeChainException;
import com.example.geohousing.properties.domain.Property;
import com.example.geohousing.properties.domain.PropertyId;
import com.example.geohousing.properties.domain.PropertyStatus;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Answers other modules' questions about properties — the implementation of {@link
 * PropertyCatalog}.
 */
public final class PropertyCatalogService implements PropertyCatalog {

  /**
   * How many merges deep the resolver will follow. Merges are rare administrative corrections, so a
   * real chain is one or two hops; the cap exists because nothing stops an administrator merging A
   * into B and then B into A, which would otherwise loop forever.
   */
  static final int MAX_MERGE_HOPS = 8;

  private final PropertyRepository propertyRepository;

  public PropertyCatalogService(PropertyRepository propertyRepository) {
    this.propertyRepository = Objects.requireNonNull(propertyRepository, "propertyRepository");
  }

  @Override
  public Optional<PropertySummary> findSurviving(UUID propertyId) {
    Objects.requireNonNull(propertyId, "propertyId");

    Optional<Property> found = propertyRepository.findById(PropertyId.of(propertyId));
    for (int hop = 0; hop < MAX_MERGE_HOPS; hop++) {
      if (found.isEmpty()) {
        // Either the requested property does not exist, or a merge points at one that no longer
        // does. Both are "no such property" from the caller's side.
        return Optional.empty();
      }
      Property property = found.get();
      if (property.status() != PropertyStatus.MERGED) {
        return Optional.of(summarize(property));
      }
      found = propertyRepository.findById(property.mergedIntoPropertyId().orElseThrow());
    }
    throw new UnresolvableMergeChainException(propertyId);
  }

  private static PropertySummary summarize(Property property) {
    return new PropertySummary(
        property.id().value(), property.canonicalName(), visibilityOf(property.status()));
  }

  /**
   * {@code DRAFT} properties are user-contributed and already publicly readable, so they are public
   * here too; only an administrator hiding a property withholds it. {@code MERGED} never reaches
   * this point — the resolver has followed it.
   */
  private static PropertyVisibility visibilityOf(PropertyStatus status) {
    return status == PropertyStatus.HIDDEN
        ? PropertyVisibility.WITHHELD
        : PropertyVisibility.PUBLIC;
  }
}
