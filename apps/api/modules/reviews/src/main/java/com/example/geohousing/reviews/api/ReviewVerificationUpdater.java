package com.example.geohousing.reviews.api;

import java.util.UUID;

/**
 * The reviews module's <em>inbound</em> published contract: the verification module projects its
 * decision onto a review through this port, so the review can display a trust badge without reviews
 * ever reading the verification tables.
 *
 * <p>This is the first inbound contract reviews owns, and the projection is deliberately one-way.
 * Reviews does not call verification (that would make the two modules mutually dependent, which the
 * no-cycles rule forbids); verification pushes here when it decides. A review written before its
 * author is verified picks up the tier on that push; a review written after approval is a known gap
 * closed by a later re-push.
 */
public interface ReviewVerificationUpdater {

  /**
   * Sets the verification tier on the author's live review of a property, if they have one.
   *
   * <p>The tier is a projection: it replaces whatever was there (an approval raises it, a
   * revocation or expiry lowers it back to {@code UNVERIFIED}). A terminal review is never touched
   * — the tier only matters while a review can still be shown.
   *
   * @return {@code true} if a live review was updated, {@code false} if the author has no live
   *     review of the property to attach the tier to
   */
  boolean applyTier(UUID authorAccountId, UUID propertyId, ReviewVerificationTier tier);
}
