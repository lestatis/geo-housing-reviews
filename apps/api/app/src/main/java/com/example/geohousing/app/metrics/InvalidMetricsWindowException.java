package com.example.geohousing.app.metrics;

/**
 * A window that cannot be answered as asked.
 *
 * <p>Its own type rather than a bare {@link IllegalArgumentException}, because the advice above it
 * turns what it catches into "your request was wrong". Catching the general exception meant any
 * internal inconsistency — a bad predicate, a race between two counts — reached an administrator as
 * a complaint about their dates, sending them to fix something that was never broken while the real
 * fault went unreported.
 */
public class InvalidMetricsWindowException extends IllegalArgumentException {

  private static final long serialVersionUID = 1L;

  public InvalidMetricsWindowException(String message) {
    super(message);
  }
}
