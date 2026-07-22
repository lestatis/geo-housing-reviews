package com.example.geohousing.properties.api;

import java.util.UUID;

/**
 * Raised when following a property's merges does not reach a surviving property — a chain longer
 * than the catalogue should ever produce, or a cycle.
 *
 * <p>This is corrupt catalogue data, not a caller mistake, so it fails loudly instead of quietly
 * returning "no such property": silently dropping a review because two properties point at each
 * other would hide the fault instead of getting it fixed.
 */
public class UnresolvableMergeChainException extends RuntimeException {

  public UnresolvableMergeChainException(UUID propertyId) {
    super("merge chain does not resolve to a surviving property, starting at " + propertyId);
  }
}
