package com.example.geohousing.properties.application;

import com.example.geohousing.properties.domain.Coordinates;
import java.util.Optional;

/**
 * What someone is looking for: a fragment they typed, a place they are standing, or both.
 *
 * <p>Refuses a query with neither. An empty search would return the whole catalogue ordered by
 * nothing in particular, which is what the listing endpoint is for — and at catalogue scale it is a
 * table scan a caller can trigger at will.
 */
public record PropertySearchQuery(String text, Coordinates point, double radiusMeters, int limit) {

  /** Applied when the caller asks for nothing specific. */
  public static final int DEFAULT_LIMIT = 20;

  /** Hard cap, so a client cannot pull the catalogue in one call. */
  public static final int MAX_LIMIT = 50;

  /** Applied when a point is given without a radius. */
  public static final double DEFAULT_RADIUS_METERS = 2_000;

  /** Beyond this a "nearby" search stops meaning anything. */
  public static final double MAX_RADIUS_METERS = 50_000;

  public PropertySearchQuery {
    text = text == null || text.isBlank() ? null : text.trim();
    if (text == null && point == null) {
      throw new IllegalArgumentException("a search needs either text or a point");
    }
    radiusMeters =
        radiusMeters <= 0 ? DEFAULT_RADIUS_METERS : Math.min(radiusMeters, MAX_RADIUS_METERS);
    limit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
  }

  public static PropertySearchQuery of(
      String text, Double latitude, Double longitude, Double radiusMeters, Integer limit) {
    Coordinates point =
        latitude == null || longitude == null ? null : Coordinates.of(latitude, longitude);
    return new PropertySearchQuery(
        text,
        point,
        radiusMeters == null ? 0 : radiusMeters,
        limit == null ? DEFAULT_LIMIT : limit);
  }

  public Optional<String> textFragment() {
    return Optional.ofNullable(text);
  }

  public Optional<Coordinates> centre() {
    return Optional.ofNullable(point);
  }
}
