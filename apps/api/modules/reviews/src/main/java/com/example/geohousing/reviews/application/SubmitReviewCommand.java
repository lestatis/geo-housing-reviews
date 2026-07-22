package com.example.geohousing.reviews.application;

import com.example.geohousing.reviews.domain.AuthorId;
import com.example.geohousing.reviews.domain.PropertyRef;
import com.example.geohousing.reviews.domain.RelationshipType;
import com.example.geohousing.reviews.domain.ResidencePeriod;
import java.util.Objects;

/**
 * Request to submit a new review of a property. {@code residencePeriod} is optional — an author who
 * does not want to state when they lived there may leave it out.
 */
public record SubmitReviewCommand(
    PropertyRef propertyRef,
    AuthorId authorId,
    RelationshipType relationshipType,
    ResidencePeriod residencePeriod,
    ReviewContent content) {

  public SubmitReviewCommand {
    Objects.requireNonNull(propertyRef, "propertyRef");
    Objects.requireNonNull(authorId, "authorId");
    Objects.requireNonNull(relationshipType, "relationshipType");
    Objects.requireNonNull(content, "content");
  }
}
