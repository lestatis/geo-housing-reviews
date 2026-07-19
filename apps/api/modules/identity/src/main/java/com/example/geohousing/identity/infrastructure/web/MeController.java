package com.example.geohousing.identity.infrastructure.web;

import com.example.geohousing.identity.application.ProfileService;
import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.Pseudonym;
import com.example.geohousing.identity.domain.PublicProfile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service profile endpoints for the authenticated user. Every action is scoped to the caller's
 * own account: the account id comes from the authentication established by the identity JWT
 * converter (its principal name is the opaque account id, never the raw subject), so there is no
 * client-supplied identifier to authorize against and no way to address another user's profile.
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

  private static final String ROLE_PREFIX = "ROLE_";

  private final ProfileService profileService;

  public MeController(ProfileService profileService) {
    this.profileService = profileService;
  }

  @GetMapping
  MeResponse me(Authentication authentication) {
    AccountId accountId = WebAuthentication.accountId(authentication);
    PublicProfile profile = profileService.getProfile(accountId);
    return MeResponse.from(accountId, role(authentication), profile);
  }

  @PatchMapping("/profile")
  MeResponse updateProfile(
      Authentication authentication, @RequestBody UpdateProfileRequest request) {
    AccountId accountId = WebAuthentication.accountId(authentication);
    PublicProfile updated =
        profileService.updateProfile(
            accountId,
            Pseudonym.of(request.pseudonym()),
            request.avatarUrl(),
            request.locale(),
            request.version());
    return MeResponse.from(accountId, role(authentication), updated);
  }

  private static String role(Authentication authentication) {
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .filter(authority -> authority.startsWith(ROLE_PREFIX))
        .map(authority -> authority.substring(ROLE_PREFIX.length()))
        .findFirst()
        .orElse("USER");
  }
}
