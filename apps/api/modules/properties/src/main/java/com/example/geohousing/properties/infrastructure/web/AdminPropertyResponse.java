package com.example.geohousing.properties.infrastructure.web;

import com.example.geohousing.properties.domain.Property;
import com.example.geohousing.properties.domain.PropertyAlias;
import com.example.geohousing.properties.domain.PropertyId;
import java.time.Instant;
import java.util.List;

/**
 * A property as an administrator sees it: the public view plus the address text a resident typed.
 *
 * <p>Admin-only by construction — the public {@link PropertyResponse} carries no {@code
 * originalText}, so free text that may contain an apartment number cannot reach an anonymous reader
 * (DECISION_LOG P-018).
 */
public record AdminPropertyResponse(
    String propertyId,
    String type,
    String status,
    String canonicalName,
    AdminAddressView address,
    Double latitude,
    Double longitude,
    List<PropertyResponse.AliasView> aliases,
    String parentPropertyId,
    String mergedIntoPropertyId,
    Instant createdAt,
    Instant updatedAt,
    long version) {

  static AdminPropertyResponse from(Property property) {
    return new AdminPropertyResponse(
        property.id().value().toString(),
        property.type().name(),
        property.status().name(),
        property.canonicalName(),
        property
            .address()
            .map(
                a ->
                    new AdminAddressView(
                        a.country(),
                        a.city(),
                        a.district(),
                        a.street(),
                        a.building(),
                        a.originalText()))
            .orElse(null),
        property.coordinates().map(c -> c.latitude()).orElse(null),
        property.coordinates().map(c -> c.longitude()).orElse(null),
        property.aliases().stream().map(AdminPropertyResponse::toAliasView).toList(),
        property.parentPropertyId().map(PropertyId::value).map(Object::toString).orElse(null),
        property.mergedIntoPropertyId().map(PropertyId::value).map(Object::toString).orElse(null),
        property.createdAt(),
        property.updatedAt(),
        property.version());
  }

  private static PropertyResponse.AliasView toAliasView(PropertyAlias alias) {
    return new PropertyResponse.AliasView(
        alias.locale(), alias.name(), alias.source().name(), alias.confidence());
  }
}
