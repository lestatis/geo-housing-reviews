package com.example.geohousing.reviews.infrastructure.properties;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.geohousing.properties.api.PropertyCatalog;
import com.example.geohousing.properties.api.PropertySummary;
import com.example.geohousing.properties.api.PropertyVisibility;
import com.example.geohousing.reviews.application.PropertyReviewability;
import com.example.geohousing.reviews.domain.PropertyRef;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogPropertyLookupTest {

  private static final PropertyRef REQUESTED = PropertyRef.of(UUID.randomUUID());

  private static CatalogPropertyLookup lookupSeeing(PropertySummary summary) {
    PropertyCatalog catalog = propertyId -> Optional.ofNullable(summary);
    return new CatalogPropertyLookup(catalog);
  }

  private static PropertySummary summary(UUID id, PropertyVisibility visibility) {
    return new PropertySummary(id, "Vake Tower", visibility);
  }

  @Test
  void aPublicPropertyAcceptsNewReviews() {
    PropertyReviewability reviewability =
        lookupSeeing(summary(REQUESTED.value(), PropertyVisibility.PUBLIC))
            .findReviewability(REQUESTED)
            .orElseThrow();

    assertThat(reviewability.reviewTarget()).isEqualTo(REQUESTED);
    assertThat(reviewability.acceptsNewReviews()).isTrue();
  }

  @Test
  void aWithheldPropertyIsKnownButTakesNoNewReviews() {
    PropertyReviewability reviewability =
        lookupSeeing(summary(REQUESTED.value(), PropertyVisibility.WITHHELD))
            .findReviewability(REQUESTED)
            .orElseThrow();

    assertThat(reviewability.acceptsNewReviews()).isFalse();
  }

  @Test
  void theReviewTargetIsWhicheverPropertyTheCatalogueResolvedTo() {
    UUID survivor = UUID.randomUUID();

    PropertyReviewability reviewability =
        lookupSeeing(summary(survivor, PropertyVisibility.PUBLIC))
            .findReviewability(REQUESTED)
            .orElseThrow();

    assertThat(reviewability.reviewTarget()).isEqualTo(PropertyRef.of(survivor));
  }

  @Test
  void anUnknownPropertyStaysUnknown() {
    assertThat(lookupSeeing(null).findReviewability(REQUESTED)).isEmpty();
  }
}
