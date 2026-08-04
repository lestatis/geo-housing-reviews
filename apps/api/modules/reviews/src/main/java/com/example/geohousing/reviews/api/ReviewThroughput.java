package com.example.geohousing.reviews.api;

/**
 * What was done to reviews in a window.
 *
 * <p>Published and removed together, deliberately. Either number alone flatters or alarms: a
 * platform publishing a hundred and removing eighty is not the same as one publishing a hundred and
 * removing two, and a screen showing only the first cannot tell them apart.
 */
public record ReviewThroughput(long published, long removed) {

  public ReviewThroughput {
    if (published < 0 || removed < 0) {
      throw new IllegalArgumentException("a count cannot be negative");
    }
  }
}
