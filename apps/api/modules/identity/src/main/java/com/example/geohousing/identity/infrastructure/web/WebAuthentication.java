package com.example.geohousing.identity.infrastructure.web;

import com.example.geohousing.identity.domain.AccountId;
import java.util.UUID;
import org.springframework.security.core.Authentication;

/**
 * Resolves the authenticated caller's account id. The identity JWT converter sets the principal
 * name to the opaque account id (never the raw external subject), so this is the single, trusted
 * source of "who is calling" for identity's controllers.
 */
final class WebAuthentication {

  private WebAuthentication() {}

  static AccountId accountId(Authentication authentication) {
    return AccountId.of(UUID.fromString(authentication.getName()));
  }
}
