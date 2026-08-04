package com.example.geohousing.app.audit;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps unanswerable timeline requests to RFC 7807 Problem Details ({@code docs/API_GUIDELINES.md}).
 *
 * <p>Without this, a mistyped actor id or a backwards window reached the container as an unhandled
 * exception and came back as a 500 — telling an administrator the audit log had broken when in fact
 * they had asked it something it could not read. A 500 also invites a retry of a request that will
 * never succeed.
 *
 * <p>Scoped to this package, like every other module's handler: a global advice here would answer
 * for other modules' controllers and report their failures with audit error codes.
 */
@RestControllerAdvice(basePackages = "com.example.geohousing.app.audit")
class AuditExceptionHandler {

  @ExceptionHandler(InvalidAuditQueryException.class)
  ProblemDetail handleInvalidQuery(InvalidAuditQueryException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    problem.setTitle("Invalid audit query");
    problem.setProperty("code", "INVALID_AUDIT_QUERY");
    problem.setProperty(
        "fieldErrors", List.of(Map.of("field", exception.field(), "code", exception.code())));
    return problem;
  }
}
