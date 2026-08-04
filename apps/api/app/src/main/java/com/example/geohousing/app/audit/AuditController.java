package com.example.geohousing.app.audit;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.time.Duration;
import java.time.Instant;
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

  /** Long enough to cover a week's activity, short enough that a first page is cheap. */
  private static final Duration DEFAULT_WINDOW = Duration.ofDays(7);

  private final AuditTimelineService timeline;

  AuditController(AuditTimelineService timeline) {
    this.timeline = timeline;
  }

  /**
   * A page of the timeline.
   *
   * <p>{@code since} is inclusive and {@code until} exclusive, which is what lets a caller ask for
   * a whole calendar day without guessing how many fractional seconds to subtract from midnight.
   * {@code cursor} continues a previous page and is opaque: it comes back in {@code nextCursor} and
   * goes out again unchanged.
   */
  @GetMapping
  // Both responses are spelled out: declaring one @ApiResponse replaces the derived set rather
  // than adding to it, and a documented 400 that quietly deleted the 200 would take the response
  // schema out of the generated client.
  @ApiResponse(
      responseCode = "200",
      description = "A page of the timeline, newest first.",
      content = @Content(schema = @Schema(implementation = AuditTimelineResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description =
          "The query cannot be answered as asked — an actor filter that is not an account id, a"
              + " window that ends before it starts, or a cursor that cannot be read. The body"
              + " names the parameter in fieldErrors.",
      content =
          @Content(
              mediaType = "application/problem+json",
              schema = @Schema(implementation = org.springframework.http.ProblemDetail.class)))
  AuditTimelineResponse recorded(
      Authentication authentication,
      @Parameter(
              description =
                  "Inclusive start of the window. Defaults to seven days ago, and is taken from the"
                      + " cursor when one is given.")
          @RequestParam(name = "since", required = false)
          Instant since,
      @Parameter(
              description =
                  "Exclusive end of the window. To cover a whole calendar day, pass the start of"
                      + " the following day. Defaults to now.")
          @RequestParam(name = "until", required = false)
          Instant until,
      @Parameter(description = "Show only this account's actions. Absent means everyone's.")
          @RequestParam(name = "actor", required = false)
          String actor,
      @Parameter(
              description =
                  "Continue a previous page: pass back its nextCursor unchanged. The cursor carries"
                      + " the window and actor filter it came from; restating them is optional, but"
                      + " asking for different ones is refused rather than answered with a fragment"
                      + " of a different timeline.")
          @RequestParam(name = "cursor", required = false)
          String cursor,
      @RequestParam(name = "limit", required = false) Integer limit) {
    AuditQuery query = asked(since, until, actorFrom(actor), cursor, limit);

    AuditPage page = timeline.recorded(query, callerOf(authentication));
    return new AuditTimelineResponse(
        page.entries().stream().map(AuditEntryView::from).toList(),
        page.nextCursor() == null ? null : AuditPageToken.encode(page.nextCursor(), query));
  }

  /**
   * The query as asked, or as continued.
   *
   * <p>A continuation takes its window and actor from the token rather than from the request. That
   * is what stops a cursor being carried onto a different search, where it would resume partway
   * through a timeline the caller has never seen the start of and then report no further pages.
   */
  private static AuditQuery asked(
      Instant since, Instant until, UUID actor, String cursor, Integer limit) {
    if (cursor == null || cursor.isBlank()) {
      return new AuditQuery(
          since == null ? Instant.now().minus(DEFAULT_WINDOW) : since,
          until == null ? Instant.now() : until,
          null,
          actor,
          limit == null ? 0 : limit);
    }
    AuditPageToken token = AuditPageToken.decode(cursor);
    token.verifyContinues(since, until, actor);
    return new AuditQuery(
        token.since(),
        token.until(),
        token.position(),
        token.actorAccountId(),
        limit == null ? 0 : limit);
  }

  /**
   * Refused rather than ignored. An unreadable actor filter treated as "everyone" answers a much
   * broader question than the one asked, and looks like an answer.
   */
  private static UUID actorFrom(String actor) {
    if (actor == null || actor.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(actor);
    } catch (IllegalArgumentException notAnId) {
      throw new InvalidAuditQueryException(
          "actor", "NOT_AN_ID", "The actor filter is not an account id.");
    }
  }

  private static UUID callerOf(Authentication authentication) {
    return UUID.fromString(authentication.getName());
  }
}
