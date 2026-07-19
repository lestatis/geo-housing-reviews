package com.example.geohousing.identity.infrastructure.security;

import com.example.geohousing.identity.application.AuthSubjectHasher;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 implementation of {@link AuthSubjectHasher} (ADR-0006). Renders the digest as 64
 * lowercase hex characters, the exact form {@code identity.account.auth_subject_hash} stores and
 * that migration {@code V2.4}'s {@code account_auth_subject_hash_format_check} enforces — a base64
 * rendering (44 chars) would be blank-padded to 64 and silently accepted without that check, and
 * still rejected by it now.
 *
 * <p>The pepper is a server-side secret; hashing with the pepper (rather than a plain digest)
 * prevents an attacker who obtains the table from correlating a stored hash against a guessed list
 * of subjects offline. A blank pepper is rejected at construction so the context fails to start
 * rather than hashing with an empty key.
 */
public final class HmacAuthSubjectHasher implements AuthSubjectHasher {

  private static final String ALGORITHM = "HmacSHA256";

  private final byte[] pepper;

  public HmacAuthSubjectHasher(String pepper) {
    if (pepper == null || pepper.isBlank()) {
      throw new IllegalArgumentException(
          "auth subject pepper must not be blank; set IDENTITY_AUTH_SUBJECT_PEPPER");
    }
    this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
  }

  @Override
  public String hash(String rawAuthSubject) {
    if (rawAuthSubject == null || rawAuthSubject.isBlank()) {
      throw new IllegalArgumentException("rawAuthSubject must not be blank");
    }
    // Mac is not thread-safe, so a fresh instance is created per call.
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(new SecretKeySpec(pepper, ALGORITHM));
      byte[] digest = mac.doFinal(rawAuthSubject.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
      throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
    }
  }
}
