package com.example.geohousing.reviews.api;

import java.util.Objects;
import java.util.UUID;

/**
 * What another module may learn about a review in order to moderate it: who wrote it, which version
 * is current, and whether it is publicly visible.
 *
 * <p>Deliberately carries <em>no review content</em>. A case workflow does not need the text — it
 * needs the author (to refuse a self-report, and to know who an adverse decision affects) and the
 * version (to stamp a decision and to detect that the content changed underneath it). A moderator
 * who has to read the review does so through this module's own admin endpoint, where that access is
 * scoped and audited by the module that owns the content. Copying review text out would put the
 * same sensitive material in a second place under a second set of access rules.
 */
public record ModeratableReview(
    UUID reviewId, UUID authorAccountId, long version, boolean published) {

  public ModeratableReview {
    Objects.requireNonNull(reviewId, "reviewId");
    Objects.requireNonNull(authorAccountId, "authorAccountId");
  }
}
