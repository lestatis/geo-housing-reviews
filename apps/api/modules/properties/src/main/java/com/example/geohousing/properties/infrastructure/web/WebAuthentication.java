package com.example.geohousing.properties.infrastructure.web;

import com.example.geohousing.properties.domain.AdminId;
import com.example.geohousing.properties.domain.CreatorId;
import java.security.Principal;
import java.util.UUID;

/**
 * Resolves the authenticated caller's account id. The identity module's JWT converter sets the
 * principal name to the opaque account id (never the raw external subject).
 *
 * <p>Takes the JDK {@link Principal} rather than Spring Security's {@code Authentication} on
 * purpose: this module does no security, so it needs no Spring Security dependency — Spring MVC
 * injects {@link Principal} for the authenticated request just the same.
 */
final class WebAuthentication {

  private WebAuthentication() {}

  static CreatorId creatorId(Principal principal) {
    return CreatorId.of(UUID.fromString(principal.getName()));
  }

  static AdminId adminId(Principal principal) {
    return AdminId.of(UUID.fromString(principal.getName()));
  }
}
