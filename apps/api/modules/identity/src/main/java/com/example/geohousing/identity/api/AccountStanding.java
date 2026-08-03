package com.example.geohousing.identity.api;

import java.util.UUID;

/**
 * Whether an account is currently allowed to contribute.
 *
 * <p>Identity owns restrictions; every other module asks. This is the whole of what they may ask —
 * not who restricted them, not why, not until when. A module deciding whether to accept a review
 * needs a yes or a no, and giving it the reason would invite that reason into a response the
 * affected person did not ask for.
 *
 * <p>Identity learns nothing about its callers in return: the dependency is one-way, so reviews and
 * moderation may depend on identity and identity never depends on them.
 */
public interface AccountStanding {

  /**
   * True when a restriction is in force right now.
   *
   * <p>Computed from the restriction's window rather than a flag on the account, so it can never go
   * stale when a bounded restriction simply runs out.
   */
  boolean isRestricted(UUID accountId);
}
