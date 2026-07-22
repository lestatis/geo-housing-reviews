package com.example.geohousing.reviews.infrastructure.web;

import com.example.geohousing.reviews.application.ReviewViewer;
import com.example.geohousing.reviews.domain.AuthorId;
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

  static AuthorId authorId(Principal principal) {
    return AuthorId.of(UUID.fromString(principal.getName()));
  }

  /**
   * The viewer for a public endpoint: always a plain user, never a moderator.
   *
   * <p>Elevated visibility is not granted here even to an administrator. Moderators read
   * unpublished content through the moderation queue, where the access is scoped and auditable —
   * quietly widening what the public endpoints return would put that access outside any record of
   * it.
   */
  static ReviewViewer publicViewer(Principal principal) {
    return ReviewViewer.user(authorId(principal));
  }
}
