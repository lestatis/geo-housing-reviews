package com.example.geohousing.reviews.api;

import java.time.Instant;

/** What the reviews module can say about its own queue and what moderation did to its content. */
public interface ReviewMetrics {

  /** Reviews submitted and not yet decided — the work waiting on a moderator. */
  long awaitingModeration();

  /**
   * Moderation outcomes applied to reviews in a window, {@code from} inclusive, {@code until}
   * exclusive.
   *
   * <p>Read from the append-only audit rows rather than from current status: a review published on
   * Monday and removed on Friday is one publication that really happened, and counting today's
   * statuses would erase it.
   */
  ReviewThroughput between(Instant from, Instant until);
}
