package com.example.geohousing.verification.application;

/**
 * Raised when an upload exceeds the configured evidence size cap. Detected while reading, before an
 * object is written, so an oversized upload never leaves a partial object behind.
 */
public class EvidenceTooLargeException extends RuntimeException {

  public EvidenceTooLargeException(long limitBytes) {
    super("evidence exceeds the maximum size of " + limitBytes + " bytes");
  }
}
