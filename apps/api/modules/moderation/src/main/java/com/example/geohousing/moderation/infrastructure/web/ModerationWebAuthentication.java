package com.example.geohousing.moderation.infrastructure.web;

import com.example.geohousing.moderation.domain.ReporterId;
import java.security.Principal;
import java.util.UUID;

/**
 * Resolves the authenticated caller as a reporter. The identity module's JWT converter sets the
 * principal name to the opaque account id, never the raw external subject.
 *
 * <p>Takes the JDK {@link Principal} rather than Spring Security's {@code Authentication} for the
 * same reason as the reviews module: this module does no security, so it needs no Spring Security
 * dependency, and Spring MVC injects {@link Principal} for an authenticated request just the same.
 */
final class ModerationWebAuthentication {

  private ModerationWebAuthentication() {}

  static ReporterId reporterId(Principal principal) {
    return ReporterId.of(UUID.fromString(principal.getName()));
  }
}
