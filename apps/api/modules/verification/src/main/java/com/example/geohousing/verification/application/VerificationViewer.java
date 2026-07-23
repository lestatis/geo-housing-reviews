package com.example.geohousing.verification.application;

import com.example.geohousing.verification.domain.AccountRef;
import java.util.Objects;

/**
 * Who is asking to see a verification case. A case is a private workflow (SECURITY_PRIVACY.md:
 * confidential), so unlike a review there is no anonymous viewer — a case is visible only to the
 * account it belongs to and to moderators.
 *
 * <p>The moderator flag is decided by the web layer from the caller's role; the application layer
 * is told the answer rather than resolving roles itself.
 */
public record VerificationViewer(AccountRef accountRef, boolean moderator) {

  public VerificationViewer {
    Objects.requireNonNull(accountRef, "accountRef");
  }

  public static VerificationViewer account(AccountRef accountRef) {
    return new VerificationViewer(accountRef, false);
  }

  public static VerificationViewer moderator(AccountRef accountRef) {
    return new VerificationViewer(accountRef, true);
  }

  public boolean owns(AccountRef owner) {
    return accountRef.equals(owner);
  }
}
