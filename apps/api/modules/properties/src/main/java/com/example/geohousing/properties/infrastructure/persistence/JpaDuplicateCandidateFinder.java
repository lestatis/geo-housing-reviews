package com.example.geohousing.properties.infrastructure.persistence;

import com.example.geohousing.properties.application.DuplicateCandidate;
import com.example.geohousing.properties.application.DuplicateCandidateFinder;
import com.example.geohousing.properties.domain.Address;
import com.example.geohousing.properties.domain.Coordinates;
import com.example.geohousing.properties.domain.PropertyId;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostGIS-backed {@link DuplicateCandidateFinder}: matches on normalized canonical-name equality or
 * geographic proximity (ADR-0007). Address-component matching is a later refinement; for now the
 * {@code address} argument is unused.
 */
@Repository
public class JpaDuplicateCandidateFinder implements DuplicateCandidateFinder {

  /** Two buildings within this distance are plausibly the same place. Tunable. */
  private static final double RADIUS_METERS = 75.0;

  private static final int MAX_RESULTS = 20;

  private final SpringDataPropertyRepository properties;

  public JpaDuplicateCandidateFinder(SpringDataPropertyRepository properties) {
    this.properties = properties;
  }

  @Override
  @Transactional(readOnly = true)
  public List<DuplicateCandidate> findCandidates(
      String canonicalName, Address address, Coordinates coordinates) {
    boolean hasPoint = coordinates != null;
    double latitude = hasPoint ? coordinates.latitude() : 0;
    double longitude = hasPoint ? coordinates.longitude() : 0;

    return properties
        .findDuplicateCandidates(
            canonicalName, hasPoint, latitude, longitude, RADIUS_METERS, MAX_RESULTS)
        .stream()
        .map(p -> new DuplicateCandidate(PropertyId.of(p.getId()), p.getCanonicalName()))
        .toList();
  }
}
