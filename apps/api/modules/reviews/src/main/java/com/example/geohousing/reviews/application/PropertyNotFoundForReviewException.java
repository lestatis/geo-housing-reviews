package com.example.geohousing.reviews.application;

import com.example.geohousing.reviews.domain.PropertyRef;

/**
 * Raised when a review names a property the catalogue does not have. Reviews owns this exception
 * rather than reusing the properties module's — modules do not share exception types across their
 * boundary.
 */
public class PropertyNotFoundForReviewException extends RuntimeException {

  public PropertyNotFoundForReviewException(PropertyRef propertyRef) {
    super("property not found: " + propertyRef.value());
  }
}
