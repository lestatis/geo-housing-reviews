package com.example.geohousing.app.audit;

import com.example.geohousing.shared.audit.AuditCursor;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * A continuation: a position in the timeline, <em>and</em> the query it is a position in.
 *
 * <p>A position alone is not enough. Carried onto a different actor filter or a different window it
 * resumes partway through a timeline whose beginning the caller has never seen, and on reaching the
 * end reports no further pages — telling an administrator they have read the whole history when
 * they have read a fragment of it. For an audit log that is the worst available answer, so the
 * query travels with the position and a request that asks something else is refused.
 *
 * <p>Opaque, not authenticated. It carries no authority: the endpoint is {@code ADMIN}-gated, and
 * anything a forged token could express is a query the same caller could simply send. Signing it
 * would add a key to manage without adding a property worth having.
 *
 * <p>The wire format lives here rather than on {@link AuditCursor} because which query a page
 * belongs to is an application concern; the cursor is only a place in an ordering, which is what
 * every module's adapter needs and all it needs.
 */
record AuditPageToken(AuditCursor position, Instant since, Instant until, UUID actorAccountId) {

  private static final String SEPARATOR = "|";
  private static final String NOBODY = "";
  private static final int FIELDS = 6;

  AuditPageToken {
    Objects.requireNonNull(position, "position");
    Objects.requireNonNull(since, "since");
    Objects.requireNonNull(until, "until");
  }

  static String encode(AuditCursor position, AuditQuery query) {
    String raw =
        String.join(
            SEPARATOR,
            position.at().toString(),
            position.module(),
            position.id().toString(),
            query.from().toString(),
            query.until().toString(),
            query.actor().map(UUID::toString).orElse(NOBODY));
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  static AuditPageToken decode(String encoded) {
    if (encoded == null || encoded.isBlank()) {
      throw unreadable();
    }
    String raw;
    try {
      raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException notBase64) {
      throw unreadable();
    }
    String[] parts = raw.split("\\" + SEPARATOR, -1);
    if (parts.length != FIELDS) {
      throw unreadable();
    }
    try {
      return new AuditPageToken(
          new AuditCursor(Instant.parse(parts[0]), parts[1], UUID.fromString(parts[2])),
          Instant.parse(parts[3]),
          Instant.parse(parts[4]),
          parts[5].isEmpty() ? null : UUID.fromString(parts[5]));
    } catch (RuntimeException notAToken) {
      throw unreadable();
    }
  }

  /**
   * Refuses this token unless the request is continuing the query it came from.
   *
   * <p>A null bound or actor means the request did not restate it, which is not a disagreement —
   * the token is the statement of which query is being continued, so the value is taken from it.
   */
  void verifyContinues(Instant requestedSince, Instant requestedUntil, UUID requestedActor) {
    boolean sameWindow =
        (requestedSince == null || requestedSince.equals(since))
            && (requestedUntil == null || requestedUntil.equals(until));
    // Omission is inheritance here too. A caller handed a nextCursor must be able to send it back
    // on its own — that is what makes it a continuation rather than a fragment of a query they
    // have to reconstruct. Naming a *different* actor is still a contradiction and still refused.
    boolean sameActor = requestedActor == null || requestedActor.equals(actorAccountId);
    if (!sameWindow || !sameActor) {
      throw new InvalidAuditQueryException(
          "cursor",
          "NOT_FROM_THIS_QUERY",
          "This page belongs to a different search. Start again from the top of the window.");
    }
  }

  private static InvalidAuditQueryException unreadable() {
    return new InvalidAuditQueryException("cursor", "UNREADABLE", "This is not a timeline cursor.");
  }
}
