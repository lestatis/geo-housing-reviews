package com.example.geohousing.identity.infrastructure.web;

import com.example.geohousing.identity.domain.UserRestriction;
import java.time.Instant;

/**
 * A restriction as an administrator reads it. Carries the moderator who placed it, because
 * accountability runs both ways and whoever reviews this needs to know who decided.
 */
public record RestrictionView(
    String restrictionId,
    String accountId,
    String scope,
    String reason,
    Instant startAt,
    Instant endAt,
    String moderatorAccountId,
    String appealStatus,
    boolean active) {

  static RestrictionView from(UserRestriction restriction, Instant asOf) {
    return new RestrictionView(
        restriction.id().toString(),
        restriction.accountId().value().toString(),
        restriction.scope().name(),
        restriction.reason(),
        restriction.startAt(),
        restriction.endAt().orElse(null),
        restriction.moderatorAccountId().map(id -> id.value().toString()).orElse(null),
        restriction.appealStatus().name(),
        restriction.isActiveAt(asOf));
  }
}
