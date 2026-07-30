package com.example.geohousing.moderation.infrastructure.web;

import com.example.geohousing.moderation.application.ReportIntakeService;
import com.example.geohousing.moderation.application.ReportQueryService;
import com.example.geohousing.moderation.domain.ModerationTargetRef;
import com.example.geohousing.moderation.domain.ModerationTargetType;
import com.example.geohousing.moderation.domain.Report;
import com.example.geohousing.moderation.domain.ReportCategory;
import com.example.geohousing.moderation.domain.ReportId;
import java.net.URI;
import java.security.Principal;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets a signed-in account raise a concern about content, and follow its own report.
 *
 * <p>There is deliberately no listing endpoint and no way to reach a case from here. A reporter's
 * whole view is the report they filed: MODERATION.md keeps the case, the moderator and the internal
 * note away from them, and SECURITY_PRIVACY.md treats attempts to identify critics as an abuse path
 * in its own right.
 */
@RestController
@RequestMapping("/api/reports")
class ReportController {

  private final ReportIntakeService intake;
  private final ReportQueryService reports;

  ReportController(ReportIntakeService intake, ReportQueryService reports) {
    this.intake = Objects.requireNonNull(intake, "intake");
    this.reports = Objects.requireNonNull(reports, "reports");
  }

  @PostMapping
  ResponseEntity<ReportResponse> submit(
      Principal principal, @RequestBody SubmitReportRequest request) {
    Report filed =
        intake.file(
            ModerationWebAuthentication.reporterId(principal),
            targetOf(request),
            categoryOf(request.category()),
            request.description());
    return ResponseEntity.created(URI.create("/api/reports/" + filed.id().value()))
        .body(ReportResponse.from(filed));
  }

  @GetMapping("/{reportId}")
  ReportResponse get(Principal principal, @PathVariable("reportId") String reportId) {
    return ReportResponse.from(
        reports.findOwn(
            ReportId.of(parseUuid(reportId)), ModerationWebAuthentication.reporterId(principal)));
  }

  private static ModerationTargetRef targetOf(SubmitReportRequest request) {
    return new ModerationTargetRef(
        parseEnum(ModerationTargetType.class, request.targetType(), "targetType"),
        parseUuid(request.targetId()));
  }

  private static ReportCategory categoryOf(String category) {
    return parseEnum(ReportCategory.class, category, "category");
  }

  private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    try {
      return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("unknown " + field + ": " + value);
    }
  }

  private static UUID parseUuid(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException | NullPointerException exception) {
      throw new IllegalArgumentException("not a valid identifier: " + value);
    }
  }
}
