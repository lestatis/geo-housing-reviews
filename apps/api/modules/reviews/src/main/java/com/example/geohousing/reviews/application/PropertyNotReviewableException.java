package com.example.geohousing.reviews.application;

import com.example.geohousing.reviews.domain.PropertyRef;

/**
 * Raised when a property exists but is not accepting reviews right now — for example one an
 * administrator has withheld. Distinct from "no such property", which is reported as not found.
 */
public class PropertyNotReviewableException extends RuntimeException {

  public PropertyNotReviewableException(PropertyRef propertyRef) {
    super("property is not accepting reviews: " + propertyRef.value());
  }
}
