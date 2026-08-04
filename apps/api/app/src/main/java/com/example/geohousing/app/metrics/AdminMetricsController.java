package com.example.geohousing.app.metrics;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.time.Instant;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Whether the platform's queues are being served, and whether appeals are overturning decisions.
 *
 * <p>{@code ADMIN}-only through the existing {@code /api/admin/**} gate. Unlike the audit timeline,
 * reading this is not itself audited: it names nobody and exposes no personal data, so there is
 * nothing here for an access review to review, and recording every dashboard load would bury the
 * {@code VIEW_ACCOUNT} and {@code VIEW_AUDIT} rows that access review depends on.
 */
@RestController
@RequestMapping("/api/admin/metrics")
class AdminMetricsController {

  private final AdminMetricsService metrics;

  AdminMetricsController(AdminMetricsService metrics) {
    this.metrics = metrics;
  }

  @GetMapping
  @ApiResponse(
      responseCode = "200",
      description = "Queue depth as it stands, and throughput over the window.",
      content = @Content(schema = @Schema(implementation = AdminMetrics.class)))
  @ApiResponse(
      responseCode = "400",
      description = "The window ends before it starts.",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = ProblemDetail.class)))
  AdminMetrics current(
      @Parameter(description = "Inclusive start of the window. Defaults to thirty days ago.")
          @RequestParam(name = "since", required = false)
          Instant since,
      @Parameter(description = "Exclusive end of the window. Defaults to now.")
          @RequestParam(name = "until", required = false)
          Instant until) {
    // Defaults are resolved by the service, which is where the injected clock lives.
    return metrics.current(since, until);
  }
}
