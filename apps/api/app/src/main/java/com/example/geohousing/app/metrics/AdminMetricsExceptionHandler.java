package com.example.geohousing.app.metrics;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Scoped to this package, like every other module's handler: a global advice would answer for other
 * controllers and report their failures as a metrics error.
 */
@RestControllerAdvice(basePackages = "com.example.geohousing.app.metrics")
class AdminMetricsExceptionHandler {

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail handleImpossibleWindow(IllegalArgumentException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "The window ends before it starts.");
    problem.setTitle("Invalid window");
    problem.setProperty("code", "INVALID_METRICS_WINDOW");
    return problem;
  }
}
