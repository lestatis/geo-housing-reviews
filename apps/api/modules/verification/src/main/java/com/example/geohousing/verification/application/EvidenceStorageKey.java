package com.example.geohousing.verification.application;

import com.example.geohousing.verification.domain.VerificationCaseId;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Where an evidence object lives in the store. Namespaced by case so a case's objects can be swept
 * together, and suffixed with a random component so a key cannot be guessed from the case id alone
 * (TRUST_VERIFICATION.md §6: no predictable public URLs).
 *
 * <p>The key is an internal locator, never a URL and never something a client supplies: a caller
 * that could choose the key could read or overwrite another case's evidence.
 */
public record EvidenceStorageKey(String value) {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int RANDOM_BYTES = 24;
  private static final Pattern SAFE = Pattern.compile("evidence/[0-9a-f-]{36}/[A-Za-z0-9_-]{8,}");

  public EvidenceStorageKey {
    Objects.requireNonNull(value, "value");
    if (!SAFE.matcher(value).matches()) {
      throw new IllegalArgumentException("not a valid evidence storage key");
    }
  }

  /** Mints a fresh, unguessable key for a case. */
  public static EvidenceStorageKey mint(VerificationCaseId caseId) {
    Objects.requireNonNull(caseId, "caseId");
    byte[] random = new byte[RANDOM_BYTES];
    RANDOM.nextBytes(random);
    String suffix = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    return new EvidenceStorageKey("evidence/" + caseId.value() + "/" + suffix);
  }

  /** The prefix holding every object of one case — what a per-case sweep deletes. */
  public static String prefixFor(VerificationCaseId caseId) {
    return "evidence/" + Objects.requireNonNull(caseId, "caseId").value() + "/";
  }
}
