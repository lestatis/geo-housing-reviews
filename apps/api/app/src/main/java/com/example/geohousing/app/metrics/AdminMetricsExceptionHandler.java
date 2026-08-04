package com.example.geohousing.app.metrics;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Scoped to this package, like every other module's handler: a global advice would answer for other
 * controllers and report their failures as a metrics error.
 *
 * <p>And scoped to one exception type, not to {@link IllegalArgumentException}. Anything else that
 * goes wrong in here is a fault on our side and must arrive as one, rather than as a 400 telling an
 * administrator to correct dates that were never the problem.
 */
@RestControllerAdvice(basePackages = "com.example.geohousing.app.metrics")
class AdminMetricsExceptionHandler {

  @ExceptionHandler(InvalidMetricsWindowException.class)
  ProblemDetail handleImpossibleWindow(InvalidMetricsWindowException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "The window ends before it starts.");
    problem.setTitle("Invalid window");
    problem.setProperty("code", "INVALID_METRICS_WINDOW");
    return problem;
  }
}
