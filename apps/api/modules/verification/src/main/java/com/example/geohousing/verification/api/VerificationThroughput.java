package com.example.geohousing.verification.api;

/**
 * Verification decisions in a window.
 *
 * <p>Approved beside rejected, for the same reason reviews pairs published with removed: an
 * approval count alone says nothing about whether the bar is being held.
 */
public record VerificationThroughput(long approved, long rejected) {

  public VerificationThroughput {
    if (approved < 0 || rejected < 0) {
      throw new IllegalArgumentException("a count cannot be negative");
    }
  }
}
