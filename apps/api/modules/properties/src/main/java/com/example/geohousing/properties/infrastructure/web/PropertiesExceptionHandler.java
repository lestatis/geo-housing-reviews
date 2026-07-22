package com.example.geohousing.properties.infrastructure.web;

import com.example.geohousing.properties.domain.IllegalPropertyStateTransitionException;
import com.example.geohousing.properties.domain.PropertyNotFoundException;
import com.example.geohousing.properties.domain.PropertyVersionConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps properties domain errors to RFC 7807 Problem Details (see {@code docs/API_GUIDELINES.md}).
 *
 * <p>Scoped to this module's web package: each module owns the mapping for its own controllers, so
 * one module's advice never answers for another's exceptions. 401/403 stay with the security chain.
 */
@RestControllerAdvice(basePackages = "com.example.geohousing.properties.infrastructure.web")
class PropertiesExceptionHandler {

  @ExceptionHandler(PropertyNotFoundException.class)
  ProblemDetail handleNotFound(PropertyNotFoundException exception) {
    return problem(
        HttpStatus.NOT_FOUND,
        "Property not found",
        "PROPERTY_NOT_FOUND",
        "No property was found for this identifier.");
  }

  @ExceptionHandler(IllegalPropertyStateTransitionException.class)
  ProblemDetail handleStateConflict(IllegalPropertyStateTransitionException exception) {
    return problem(
        HttpStatus.CONFLICT,
        "Property state conflict",
        "PROPERTY_STATE_CONFLICT",
        "The property's current status does not allow this change.");
  }

  @ExceptionHandler(PropertyVersionConflictException.class)
  ProblemDetail handleVersionConflict(PropertyVersionConflictException exception) {
    return problem(
        HttpStatus.CONFLICT,
        "Property version conflict",
        "PROPERTY_VERSION_CONFLICT",
        "The property was modified by another request. Reload and try again.");
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail handleInvalidRequest(IllegalArgumentException exception) {
    // Boundary validation from the domain value types (blank name, bad coordinates, malformed id).
    // Kept generic so no internal detail is echoed.
    return problem(
        HttpStatus.BAD_REQUEST, "Invalid request", "INVALID_REQUEST", "The request was invalid.");
  }

  private static ProblemDetail problem(
      HttpStatus status, String title, String code, String detail) {
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
    problemDetail.setTitle(title);
    problemDetail.setProperty("code", code);
    return problemDetail;
  }
}
