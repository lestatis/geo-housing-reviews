package com.example.geohousing.identity.infrastructure.web;

import com.example.geohousing.identity.application.IdempotencyKeyConflictException;
import com.example.geohousing.identity.domain.AccountClosedException;
import com.example.geohousing.identity.domain.AccountNotFoundException;
import com.example.geohousing.identity.domain.AccountRestrictedException;
import com.example.geohousing.identity.domain.InvalidPseudonymException;
import com.example.geohousing.identity.domain.OptimisticLockConflictException;
import com.example.geohousing.identity.domain.PseudonymAlreadyInUseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps identity domain exceptions to RFC 7807 Problem Details (see {@code docs/API_GUIDELINES.md}).
 * Each response carries a stable machine-readable {@code code}; none leak stack traces, SQL or
 * internal state. 401 is handled by the resource-server entry point, not here.
 */
@RestControllerAdvice
class IdentityExceptionHandler {

  @ExceptionHandler(AccountRestrictedException.class)
  ProblemDetail handleRestricted(AccountRestrictedException exception) {
    return problem(
        HttpStatus.FORBIDDEN,
        "Account restricted",
        "ACCOUNT_RESTRICTED",
        "Your account is currently restricted from editing its profile.");
  }

  @ExceptionHandler(AccountClosedException.class)
  ProblemDetail handleClosed(AccountClosedException exception) {
    return problem(
        HttpStatus.FORBIDDEN, "Account closed", "ACCOUNT_CLOSED", "This account is closed.");
  }

  @ExceptionHandler(OptimisticLockConflictException.class)
  ProblemDetail handleConflict(OptimisticLockConflictException exception) {
    return problem(
        HttpStatus.CONFLICT,
        "Profile version conflict",
        "PROFILE_VERSION_CONFLICT",
        "The profile was modified by another request. Reload and try again.");
  }

  @ExceptionHandler(PseudonymAlreadyInUseException.class)
  ProblemDetail handlePseudonymTaken(PseudonymAlreadyInUseException exception) {
    return problem(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "Pseudonym already in use",
        "PSEUDONYM_TAKEN",
        "That pseudonym is already in use.");
  }

  @ExceptionHandler(InvalidPseudonymException.class)
  ProblemDetail handleInvalidPseudonym(InvalidPseudonymException exception) {
    // The domain message states the specific rule violated (length/charset), which is safe and
    // helpful to echo back to the submitter.
    return problem(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "Invalid pseudonym",
        "INVALID_PSEUDONYM",
        exception.getMessage());
  }

  @ExceptionHandler(AccountNotFoundException.class)
  ProblemDetail handleNotFound(AccountNotFoundException exception) {
    return problem(
        HttpStatus.NOT_FOUND,
        "Account not found",
        "ACCOUNT_NOT_FOUND",
        "No profile was found for this account.");
  }

  @ExceptionHandler(IdempotencyKeyConflictException.class)
  ProblemDetail handleIdempotencyConflict(IdempotencyKeyConflictException exception) {
    return problem(
        HttpStatus.CONFLICT,
        "Idempotency key conflict",
        "IDEMPOTENCY_KEY_CONFLICT",
        "This idempotency key was already used for a different request.");
  }

  @ExceptionHandler(MissingRequestHeaderException.class)
  ProblemDetail handleMissingHeader(MissingRequestHeaderException exception) {
    if ("Idempotency-Key".equalsIgnoreCase(exception.getHeaderName())) {
      return problem(
          HttpStatus.BAD_REQUEST,
          "Idempotency key required",
          "IDEMPOTENCY_KEY_REQUIRED",
          "This operation requires an Idempotency-Key header.");
    }
    return problem(
        HttpStatus.BAD_REQUEST,
        "Missing request header",
        "MISSING_HEADER",
        "A required request header was missing.");
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail handleInvalidRequest(IllegalArgumentException exception) {
    // Boundary validation from the domain value types (e.g. a blank locale). Kept generic so no
    // internal detail is echoed.
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
