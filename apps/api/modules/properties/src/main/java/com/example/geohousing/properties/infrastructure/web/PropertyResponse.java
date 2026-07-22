package com.example.geohousing.properties.infrastructure.web;

import com.example.geohousing.properties.domain.Property;
import com.example.geohousing.properties.domain.PropertyAlias;
import com.example.geohousing.properties.domain.PropertyId;
import java.time.Instant;
import java.util.List;

/**
 * Public view of a property. {@code createdBy} is deliberately omitted: it is another user's opaque
 * account id and nothing needs it yet (least exposure, consistent with identity's DTOs).
 */
public record PropertyResponse(
    String propertyId,
    String type,
    String status,
    String canonicalName,
    AddressView address,
    Double latitude,
    Double longitude,
    List<AliasView> aliases,
    String parentPropertyId,
    String mergedIntoPropertyId,
    Instant createdAt,
    Instant updatedAt,
    long version) {

  public record AddressView(
      String country,
      String city,
      String district,
      String street,
      String building,
      String originalText) {}

  public record AliasView(String locale, String name, String source, Double confidence) {}

  static PropertyResponse from(Property property) {
    return new PropertyResponse(
        property.id().value().toString(),
        property.type().name(),
        property.status().name(),
        property.canonicalName(),
        property
            .address()
            .map(
                a ->
                    new AddressView(
                        a.country(),
                        a.city(),
                        a.district(),
                        a.street(),
                        a.building(),
                        a.originalText()))
            .orElse(null),
        property.coordinates().map(c -> c.latitude()).orElse(null),
        property.coordinates().map(c -> c.longitude()).orElse(null),
        property.aliases().stream().map(PropertyResponse::toAliasView).toList(),
        property.parentPropertyId().map(PropertyId::value).map(Object::toString).orElse(null),
        property.mergedIntoPropertyId().map(PropertyId::value).map(Object::toString).orElse(null),
        property.createdAt(),
        property.updatedAt(),
        property.version());
  }

  private static AliasView toAliasView(PropertyAlias alias) {
    return new AliasView(alias.locale(), alias.name(), alias.source().name(), alias.confidence());
  }
}
