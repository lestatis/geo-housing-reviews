package com.example.geohousing.reviews.infrastructure.web;

import com.example.geohousing.reviews.application.HelpfulSignalCount;

/**
 * The result of adding or withdrawing a helpful signal: the review's new aggregate, and nothing
 * else.
 *
 * <p>It deliberately does not echo whether the caller currently has an active signal. The caller
 * just performed the action, so they know; and keeping the response to a single public number means
 * no endpoint on this path can grow into one that discloses who signalled what.
 */
public record HelpfulSignalResponse(String reviewId, long helpfulCount) {

  static HelpfulSignalResponse of(HelpfulSignalCount count) {
    return new HelpfulSignalResponse(count.reviewId().value().toString(), count.value());
  }
}
