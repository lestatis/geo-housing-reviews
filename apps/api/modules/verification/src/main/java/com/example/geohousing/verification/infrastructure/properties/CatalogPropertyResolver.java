package com.example.geohousing.verification.infrastructure.properties;

import com.example.geohousing.properties.api.PropertyCatalog;
import com.example.geohousing.verification.application.PropertyLookup;
import com.example.geohousing.verification.domain.PropertyRef;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Answers the verification module's {@link PropertyLookup} from the properties module's published
 * catalogue. Only this class knows another module exists; the application layer keeps talking to
 * its own port.
 */
@Component
public class CatalogPropertyResolver implements PropertyLookup {

  private final PropertyCatalog propertyCatalog;

  public CatalogPropertyResolver(PropertyCatalog propertyCatalog) {
    this.propertyCatalog = Objects.requireNonNull(propertyCatalog, "propertyCatalog");
  }

  @Override
  public Optional<PropertyRef> resolve(PropertyRef propertyRef) {
    Objects.requireNonNull(propertyRef, "propertyRef");
    return propertyCatalog
        .findSurviving(propertyRef.value())
        .map(summary -> PropertyRef.of(summary.propertyId()));
  }
}
