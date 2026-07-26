package com.example.geohousing.verification.application;

import java.util.Set;

/**
 * The document formats evidence may be uploaded in. An allowlist rather than a blocklist: anything
 * not named here is refused before a byte reaches storage.
 *
 * <p>Deliberately narrow — the formats a person can produce from a phone camera or a bank/utility
 * portal. Note that validating the declared type is <em>not</em> malware scanning: scanning and
 * image re-encoding are recorded as a pre-launch requirement in ADR-0008, not implemented here.
 */
public final class EvidenceContentType {

  private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "application/pdf");

  private EvidenceContentType() {}

  public static boolean isAllowed(String contentType) {
    return contentType != null && ALLOWED.contains(contentType.toLowerCase(java.util.Locale.ROOT));
  }

  /** Normalises and validates a declared content type. */
  public static String require(String contentType) {
    if (!isAllowed(contentType)) {
      throw new IllegalArgumentException("unsupported evidence content type");
    }
    return contentType.toLowerCase(java.util.Locale.ROOT);
  }

  public static Set<String> allowed() {
    return ALLOWED;
  }
}
