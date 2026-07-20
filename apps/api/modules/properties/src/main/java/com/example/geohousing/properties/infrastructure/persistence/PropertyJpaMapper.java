package com.example.geohousing.properties.infrastructure.persistence;

import com.example.geohousing.properties.domain.Address;
import com.example.geohousing.properties.domain.Coordinates;
import com.example.geohousing.properties.domain.CreatorId;
import com.example.geohousing.properties.domain.Property;
import com.example.geohousing.properties.domain.PropertyAlias;
import com.example.geohousing.properties.domain.PropertyId;
import com.example.geohousing.properties.domain.PropertySource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class PropertyJpaMapper {

  private PropertyJpaMapper() {}

  static PropertyJpaEntity toEntity(Property property) {
    // The domain's address/aliases/sources are value objects with no identity; the surrogate row
    // ids
    // are a persistence detail generated here. Child created_at rows use the property's creation
    // time (they are written together on first persist).
    Instant createdAt = property.createdAt();

    AddressJpaEntity address =
        property
            .address()
            .map(
                a ->
                    new AddressJpaEntity(
                        UUID.randomUUID(),
                        a.country(),
                        a.city(),
                        a.district(),
                        a.street(),
                        a.building(),
                        a.originalText(),
                        createdAt))
            .orElse(null);

    List<PropertyAliasJpaEntity> aliases =
        property.aliases().stream()
            .map(
                a ->
                    new PropertyAliasJpaEntity(
                        UUID.randomUUID(),
                        a.locale(),
                        a.name(),
                        a.source(),
                        a.confidence() == null ? null : BigDecimal.valueOf(a.confidence()),
                        createdAt))
            .toList();

    List<PropertySourceJpaEntity> sources =
        property.sources().stream()
            .map(
                s ->
                    new PropertySourceJpaEntity(
                        UUID.randomUUID(), s.sourceType(), s.observedAt(), s.note(), createdAt))
            .toList();

    Double latitude = property.coordinates().map(Coordinates::latitude).orElse(null);
    Double longitude = property.coordinates().map(Coordinates::longitude).orElse(null);

    return new PropertyJpaEntity(
        property.id().value(),
        property.type(),
        property.status(),
        property.canonicalName(),
        address,
        property.parentPropertyId().map(PropertyId::value).orElse(null),
        property.mergedIntoPropertyId().map(PropertyId::value).orElse(null),
        latitude,
        longitude,
        property.createdBy().value(),
        property.createdAt(),
        property.updatedAt(),
        property.version(),
        aliases,
        sources);
  }

  static Property toDomain(PropertyJpaEntity entity) {
    Address address =
        entity.address() == null
            ? null
            : new Address(
                entity.address().country(),
                entity.address().city(),
                entity.address().district(),
                entity.address().street(),
                entity.address().building(),
                entity.address().originalText());

    Coordinates coordinates =
        (entity.latitude() != null && entity.longitude() != null)
            ? new Coordinates(entity.latitude(), entity.longitude())
            : null;

    List<PropertyAlias> aliases =
        entity.aliases().stream()
            .map(
                a ->
                    new PropertyAlias(
                        a.locale(),
                        a.name(),
                        a.source(),
                        a.confidence() == null ? null : a.confidence().doubleValue()))
            .toList();

    List<PropertySource> sources =
        entity.sources().stream()
            .map(s -> new PropertySource(s.sourceType(), s.observedAt(), s.note()))
            .toList();

    return Property.reconstitute(
        PropertyId.of(entity.id()),
        entity.type(),
        entity.status(),
        entity.canonicalName(),
        address,
        coordinates,
        entity.parentPropertyId() == null ? null : PropertyId.of(entity.parentPropertyId()),
        entity.mergedIntoPropertyId() == null ? null : PropertyId.of(entity.mergedIntoPropertyId()),
        aliases,
        sources,
        CreatorId.of(entity.createdBy()),
        entity.createdAt(),
        entity.updatedAt(),
        entity.version());
  }
}
