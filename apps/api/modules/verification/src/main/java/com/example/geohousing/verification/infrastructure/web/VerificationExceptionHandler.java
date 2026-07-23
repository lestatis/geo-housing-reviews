package com.example.geohousing.verification.infrastructure.web;

import com.example.geohousing.verification.application.DuplicateVerificationCaseException;
import com.example.geohousing.verification.application.PropertyNotFoundForVerificationException;
import com.example.geohousing.verification.application.VerificationAccessDeniedException;
import com.example.geohousing.verification.domain.IllegalVerificationStateTransitionException;
import com.example.geohousing.verification.domain.VerificationCaseNotFoundException;
import com.example.geohousing.verification.domain.VerificationVersionConflictException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps verification errors to RFC 7807 Problem Details (see {@code docs/API_GUIDELINES.md}).
 *
 * <p>Scoped to this module's web package: each module owns the mapping for its own controllers, so
 * one module's advice never answers for another's exceptions. 401/403 from the security chain stay
 * with it.
 */
@RestControllerAdvice(basePackages = "com.example.geohousing.verification.infrastructure.web")
class VerificationExceptionHandler {

  /**
   * A case the caller may not see is reported as missing, and this mapping keeps that promise at
   * the wire: a private workflow never confirms that a given account is verifying a given property.
   */
  @ExceptionHandler(VerificationCaseNotFoundException.class)
  ProblemDetail handleCaseNotFound(VerificationCaseNotFoundException exception) {
    return problem(
        HttpStatus.NOT_FOUND,
        "Verification case not found",
        "VERIFICATION_CASE_NOT_FOUND",
        "No verification case was found for this identifier.");
  }

  @ExceptionHandler(PropertyNotFoundForVerificationException.class)
  ProblemDetail handlePropertyNotFound(PropertyNotFoundForVerificationException exception) {
    return problem(
        HttpStatus.NOT_FOUND,
        "Property not found",
        "PROPERTY_NOT_FOUND",
        "No property was found for this identifier.");
  }

  /**
   * Carries the existing case's id and a {@code Location} header pointing at it, so a client that
   * lost the response to its first request can follow that instead of being told only "no".
   */
  @ExceptionHandler(DuplicateVerificationCaseException.class)
  ResponseEntity<ProblemDetail> handleDuplicate(DuplicateVerificationCaseException exception) {
    ProblemDetail problemDetail =
        problem(
            HttpStatus.CONFLICT,
            "Verification case already exists",
            "VERIFICATION_CASE_ALREADY_EXISTS",
            "You already have a live verification case for this property.");
    String existingCaseId = exception.existingCaseId().value().toString();
    problemDetail.setProperty("existingCaseId", existingCaseId);
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .header(HttpHeaders.LOCATION, "/api/verifications/" + existingCaseId)
        .body(problemDetail);
  }

  @ExceptionHandler(VerificationAccessDeniedException.class)
  ProblemDetail handleAccessDenied(VerificationAccessDeniedException exception) {
    return problem(
        HttpStatus.FORBIDDEN,
        "Verification access denied",
        "VERIFICATION_ACCESS_DENIED",
        "This verification case belongs to another account.");
  }

  @ExceptionHandler(IllegalVerificationStateTransitionException.class)
  ProblemDetail handleStateConflict(IllegalVerificationStateTransitionException exception) {
    return problem(
        HttpStatus.CONFLICT,
        "Verification state conflict",
        "VERIFICATION_STATE_CONFLICT",
        "The case's current status does not allow this action.");
  }

  @ExceptionHandler(VerificationVersionConflictException.class)
  ProblemDetail handleVersionConflict(VerificationVersionConflictException exception) {
    return problem(
        HttpStatus.CONFLICT,
        "Verification version conflict",
        "VERIFICATION_VERSION_CONFLICT",
        "The case was modified by another request. Reload and try again.");
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail handleInvalidRequest(IllegalArgumentException exception) {
    // Boundary validation (malformed id, unknown enum, missing field). Kept generic so no internal
    // detail is echoed.
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
