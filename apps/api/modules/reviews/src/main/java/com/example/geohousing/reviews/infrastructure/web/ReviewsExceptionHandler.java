package com.example.geohousing.reviews.infrastructure.web;

import com.example.geohousing.identity.api.RestrictedAccountException;
import com.example.geohousing.reviews.application.DuplicateReviewException;
import com.example.geohousing.reviews.application.HelpfulSignalAlreadyActiveException;
import com.example.geohousing.reviews.application.PropertyNotFoundForReviewException;
import com.example.geohousing.reviews.application.PropertyNotReviewableException;
import com.example.geohousing.reviews.application.ReviewAccessDeniedException;
import com.example.geohousing.reviews.application.SelfHelpfulSignalException;
import com.example.geohousing.reviews.domain.IllegalReviewStateTransitionException;
import com.example.geohousing.reviews.domain.ReviewNotFoundException;
import com.example.geohousing.reviews.domain.ReviewVersionConflictException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps reviews errors to RFC 7807 Problem Details (see {@code docs/API_GUIDELINES.md}).
 *
 * <p>Scoped to this module's web package: each module owns the mapping for its own controllers, so
 * one module's advice never answers for another's exceptions. 401/403 stay with the security chain.
 */
@RestControllerAdvice(basePackages = "com.example.geohousing.reviews.infrastructure.web")
class ReviewsExceptionHandler {

  /**
   * A review the caller may not see is reported as missing, and this mapping is what makes that
   * promise hold at the wire: the application already refuses to distinguish the two cases, and
   * turning either into anything but a 404 here would give the distinction back.
   */
  /**
   * A restricted account tried to contribute.
   *
   * <p>Identity owns the restriction and the reason for it; this only reports that one is in force.
   * The detail deliberately says nothing about why or until when — that belongs in one place, not
   * repeated by every module that has to refuse.
   */
  @ExceptionHandler(RestrictedAccountException.class)
  ProblemDetail handleRestrictedAccount(RestrictedAccountException exception) {
    return problem(
        HttpStatus.FORBIDDEN,
        "Account restricted",
        "ACCOUNT_RESTRICTED",
        "This account is currently restricted and cannot contribute.");
  }

  @ExceptionHandler(ReviewNotFoundException.class)
  ProblemDetail handleNotFound(ReviewNotFoundException exception) {
    return problem(
        HttpStatus.NOT_FOUND,
        "Review not found",
        "REVIEW_NOT_FOUND",
        "No review was found for this identifier.");
  }

  @ExceptionHandler(PropertyNotFoundForReviewException.class)
  ProblemDetail handlePropertyNotFound(PropertyNotFoundForReviewException exception) {
    return problem(
        HttpStatus.NOT_FOUND,
        "Property not found",
        "PROPERTY_NOT_FOUND",
        "No property was found for this identifier.");
  }

  @ExceptionHandler(PropertyNotReviewableException.class)
  ProblemDetail handlePropertyNotReviewable(PropertyNotReviewableException exception) {
    return problem(
        HttpStatus.CONFLICT,
        "Property is not accepting reviews",
        "PROPERTY_NOT_REVIEWABLE",
        "This property is not accepting new reviews.");
  }

  /**
   * Carries the existing review's id and a {@code Location} header pointing at it. A client that
   * lost the response to its first submission can follow that instead of being told only "no".
   */
  @ExceptionHandler(DuplicateReviewException.class)
  ResponseEntity<ProblemDetail> handleDuplicate(DuplicateReviewException exception) {
    ProblemDetail problemDetail =
        problem(
            HttpStatus.CONFLICT,
            "Review already exists",
            "REVIEW_ALREADY_EXISTS",
            "You already have a review of this property. Edit it instead of writing a new one.");
    String existingReviewId = exception.existingReviewId().value().toString();
    problemDetail.setProperty("existingReviewId", existingReviewId);
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .header(HttpHeaders.LOCATION, "/api/reviews/" + existingReviewId)
        .body(problemDetail);
  }

  /**
   * Signalling your own review is refused as forbidden, not hidden: the review is published, so the
   * caller can already see it and telling them why is not a disclosure.
   */
  @ExceptionHandler(SelfHelpfulSignalException.class)
  ProblemDetail handleSelfHelpfulSignal(SelfHelpfulSignalException exception) {
    return problem(
        HttpStatus.FORBIDDEN,
        "Cannot mark your own review as helpful",
        "HELPFUL_SIGNAL_SELF_NOT_ALLOWED",
        "You cannot mark your own review as helpful.");
  }

  /** A second active signal is a conflict, never a silently incremented count. */
  @ExceptionHandler(HelpfulSignalAlreadyActiveException.class)
  ProblemDetail handleDuplicateHelpfulSignal(HelpfulSignalAlreadyActiveException exception) {
    return problem(
        HttpStatus.CONFLICT,
        "Helpful signal already active",
        "HELPFUL_SIGNAL_ALREADY_ACTIVE",
        "You have already marked this review as helpful.");
  }

  @ExceptionHandler(ReviewAccessDeniedException.class)
  ProblemDetail handleAccessDenied(ReviewAccessDeniedException exception) {
    return problem(
        HttpStatus.FORBIDDEN,
        "Review access denied",
        "REVIEW_ACCESS_DENIED",
        "This review belongs to another account.");
  }

  @ExceptionHandler(IllegalReviewStateTransitionException.class)
  ProblemDetail handleStateConflict(IllegalReviewStateTransitionException exception) {
    return problem(
        HttpStatus.CONFLICT,
        "Review state conflict",
        "REVIEW_STATE_CONFLICT",
        "The review's current status does not allow this change.");
  }

  @ExceptionHandler(ReviewVersionConflictException.class)
  ProblemDetail handleVersionConflict(ReviewVersionConflictException exception) {
    return problem(
        HttpStatus.CONFLICT,
        "Review version conflict",
        "REVIEW_VERSION_CONFLICT",
        "The review was modified by another request. Reload and try again.");
  }

  @ExceptionHandler(InvalidReviewCursorException.class)
  ProblemDetail handleInvalidCursor(InvalidReviewCursorException exception) {
    return problem(
        HttpStatus.BAD_REQUEST,
        "Invalid cursor",
        "INVALID_CURSOR",
        "The cursor was not issued by this API. Start the listing again without one.");
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail handleInvalidRequest(IllegalArgumentException exception) {
    // Boundary validation from the domain value types (blank body, rating out of range, malformed
    // id, unknown enum). Kept generic so no internal detail is echoed.
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
