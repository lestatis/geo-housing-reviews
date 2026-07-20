package com.example.geohousing.properties.application;

import com.example.geohousing.properties.domain.Address;
import com.example.geohousing.properties.domain.Coordinates;
import java.util.List;

/**
 * Application port that finds existing properties likely to be the same place as one being created,
 * from its name and (optional) address/coordinates. Deterministic detection (normalized
 * name/address plus geo proximity) is implemented in the duplicate/geo chunk; the port is defined
 * here so the creation flow can depend on it.
 */
public interface DuplicateCandidateFinder {

  List<DuplicateCandidate> findCandidates(
      String canonicalName, Address address, Coordinates coordinates);
}
