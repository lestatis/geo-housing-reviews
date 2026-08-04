package com.example.geohousing.app.audit;

import com.example.geohousing.shared.audit.AuditEntry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The audit timeline: what has been done, by whom, to what, and when.
 *
 * <p>The {@code ROLE_ADMIN} gate on {@code /api/admin/**} is what restricts it — {@code
 * SECURITY_PRIVACY.md} §4 asks for an append-only log with restricted access, and until now the
 * second half was satisfied by there being no way to read it at all.
 *
 * <p>Lives in the app rather than a module because it is the one place entitled to hold every
 * module's trail at once. A module knows what it did and nothing about what the others did.
 */
@RestController
@RequestMapping("/api/admin/audit")
class AuditController {

  private final AuditTimelineService timeline;

  AuditController(AuditTimelineService timeline) {
    this.timeline = timeline;
  }

  @GetMapping
  AuditTimelineResponse recorded(
      Authentication authentication,
      @RequestParam(name = "since", required = false) Instant since,
      @RequestParam(name = "until", required = false) Instant until,
      @RequestParam(name = "actor", required = false) String actor,
      @RequestParam(name = "limit", required = false) Integer limit) {
    AuditQuery query =
        new AuditQuery(
            since == null ? Instant.now().minus(java.time.Duration.ofDays(7)) : since,
            until == null ? Instant.now() : until,
            actor == null || actor.isBlank() ? null : UUID.fromString(actor),
            limit == null ? 0 : limit);

    List<AuditEntry> entries = timeline.recorded(query, callerOf(authentication));
    return new AuditTimelineResponse(entries.stream().map(AuditEntryView::from).toList());
  }

  private static UUID callerOf(Authentication authentication) {
    return UUID.fromString(authentication.getName());
  }
}
