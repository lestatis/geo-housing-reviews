package com.example.geohousing.reviews.infrastructure.properties;

import com.example.geohousing.properties.api.PropertyCatalog;
import com.example.geohousing.properties.api.PropertySummary;
import com.example.geohousing.reviews.application.PropertyLookup;
import com.example.geohousing.reviews.application.PropertyReviewability;
import com.example.geohousing.reviews.domain.PropertyRef;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Answers the reviews module's {@link PropertyLookup} from the properties module's published
 * catalogue. This adapter is the whole of the dependency: the application layer keeps talking to
 * its own port, and only this class knows another module exists.
 *
 * <p>Translating here rather than sharing a type is the point of the boundary — whether a property
 * may be reviewed is the reviews module's policy, so properties reports availability and reviews
 * decides what that means for a review.
 */
@Component
public class CatalogPropertyLookup implements PropertyLookup {

  private final PropertyCatalog propertyCatalog;

  public CatalogPropertyLookup(PropertyCatalog propertyCatalog) {
    this.propertyCatalog = Objects.requireNonNull(propertyCatalog, "propertyCatalog");
  }

  @Override
  public Optional<PropertyReviewability> findReviewability(PropertyRef propertyRef) {
    Objects.requireNonNull(propertyRef, "propertyRef");
    return propertyCatalog
        .findSurviving(propertyRef.value())
        .map(CatalogPropertyLookup::toReviewability);
  }

  /**
   * A withheld property keeps its existing reviews readable but takes no new ones: an administrator
   * withdrew it from the catalogue, and inviting more reviews of it would work against that.
   */
  private static PropertyReviewability toReviewability(PropertySummary summary) {
    return new PropertyReviewability(PropertyRef.of(summary.propertyId()), summary.isPublic());
  }
}
