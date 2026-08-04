package com.example.geohousing.shared.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What a module recorded itself doing.
 *
 * <p>Every module that takes privileged actions implements this, so a merged timeline can ask each
 * of them the same question and collect the answers without knowing which modules exist. A module
 * answers only about its own tables — reading another module's audit rows directly is the boundary
 * violation this port exists to make unnecessary.
 *
 * <p>It lives here rather than in five {@code api} packages because five identical interfaces is
 * exactly the duplication {@link AuditEntry} is here to avoid, and because a caller collecting
 * "every module's trail" needs one type to collect.
 *
 * <p>Read-only by construction: nothing here can add, amend or remove an entry. The tables are
 * append-only and the port should not suggest otherwise.
 */
public interface AuditTrail {

  /**
   * Entries in a half-open window {@code [from, until)}, newest first.
   *
   * @param actorAccountId only this actor's entries, or null for everyone's
   * @param limit at most this many; a caller merging several trails trims again afterwards
   */
  List<AuditEntry> recorded(Instant from, Instant until, UUID actorAccountId, int limit);
}
