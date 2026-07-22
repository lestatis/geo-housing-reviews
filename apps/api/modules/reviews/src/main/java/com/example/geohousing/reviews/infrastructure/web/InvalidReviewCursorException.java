package com.example.geohousing.reviews.infrastructure.web;

/** Raised when a client supplies a cursor this API did not issue, or one that has been mangled. */
class InvalidReviewCursorException extends RuntimeException {

  InvalidReviewCursorException() {
    super("the supplied cursor is not a cursor this API issued");
  }
}
