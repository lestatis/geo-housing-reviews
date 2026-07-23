package com.example.geohousing.verification.application;

import com.example.geohousing.verification.domain.PropertyRef;

/**
 * Raised when a verification case names a property the catalogue does not have. Verification owns
 * this exception rather than reusing the properties module's — modules do not share exception types
 * across their boundary.
 */
public class PropertyNotFoundForVerificationException extends RuntimeException {

  public PropertyNotFoundForVerificationException(PropertyRef propertyRef) {
    super("property not found: " + propertyRef.value());
  }
}
