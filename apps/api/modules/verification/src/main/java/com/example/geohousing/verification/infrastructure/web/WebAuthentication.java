package com.example.geohousing.verification.infrastructure.web;

import com.example.geohousing.verification.application.VerificationViewer;
import com.example.geohousing.verification.domain.AccountRef;
import com.example.geohousing.verification.domain.ModeratorId;
import java.security.Principal;
import java.util.UUID;

/**
 * Resolves the authenticated caller's account id. The identity module's JWT converter sets the
 * principal name to the opaque account id (never the raw external subject).
 *
 * <p>Every verification endpoint is authenticated — a case is a private workflow, so there is no
 * anonymous viewer and the {@link Principal} is never null here.
 */
final class WebAuthentication {

  private WebAuthentication() {}

  static AccountRef accountRef(Principal principal) {
    return AccountRef.of(UUID.fromString(principal.getName()));
  }

  static ModeratorId moderatorId(Principal principal) {
    return ModeratorId.of(UUID.fromString(principal.getName()));
  }

  /** The viewer for a user endpoint: the caller acting on their own case. */
  static VerificationViewer ownerViewer(Principal principal) {
    return VerificationViewer.account(accountRef(principal));
  }

  /** The viewer for an admin endpoint: the moderator gate is enforced by the security chain. */
  static VerificationViewer moderatorViewer(Principal principal) {
    return VerificationViewer.moderator(accountRef(principal));
  }
}
