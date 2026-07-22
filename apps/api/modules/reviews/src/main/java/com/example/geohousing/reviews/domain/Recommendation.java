package com.example.geohousing.reviews.domain;

/** The author's overall verdict. Mirrors the check constraint on {@code reviews.review_version}. */
public enum Recommendation {
  RECOMMEND,
  NEUTRAL,
  NOT_RECOMMEND
}
