package com.example.geohousing.identity.infrastructure.security;

import com.example.geohousing.identity.application.AccountProvisioningService;
import com.example.geohousing.identity.domain.Account;
import com.example.geohousing.identity.domain.AccountClosedException;
import java.util.List;
import java.util.Objects;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Turns a validated {@link Jwt} into an authenticated account.
 *
 * <p>The single granted authority is derived <strong>only</strong> from our own {@code
 * identity.account.role} column (via the resolved {@link Account}); no claim in the JWT — {@code
 * roles}, {@code scope}, {@code authorities} or otherwise — ever contributes an authority. This is
 * the structural "never trust client-supplied role" guarantee of ADR-0005 and the API guidelines: a
 * forged elevation claim cannot grant access regardless of which IdP issued the token.
 *
 * <p>An unknown subject is auto-provisioned on first authenticated request (acceptance criterion);
 * the raw subject reaches only the hasher inside {@link AccountProvisioningService} and is never
 * persisted. A closed account cannot re-authenticate (no resurrection — ADR-0006).
 */
public final class IdentityJwtAuthenticationConverter
    implements Converter<Jwt, AbstractAuthenticationToken> {

  private static final String EMAIL_CLAIM = "email";
  private static final String DEFAULT_LOCALE = "en";

  private final AccountProvisioningService accountProvisioningService;

  public IdentityJwtAuthenticationConverter(AccountProvisioningService accountProvisioningService) {
    this.accountProvisioningService =
        Objects.requireNonNull(accountProvisioningService, "accountProvisioningService");
  }

  @Override
  public AbstractAuthenticationToken convert(Jwt jwt) {
    String subject = jwt.getSubject();
    if (subject == null || subject.isBlank()) {
      throw new InvalidBearerTokenException("token is missing a subject claim");
    }

    Account account;
    try {
      account =
          accountProvisioningService.provision(
              subject, jwt.getClaimAsString(EMAIL_CLAIM), DEFAULT_LOCALE);
    } catch (AccountClosedException closed) {
      // The subject authenticated at the IdP, but the account was deleted here and must not be
      // silently recreated. Surface as an authentication failure, not an internal error.
      throw new DisabledException("account is closed", closed);
    }

    List<GrantedAuthority> authorities =
        List.of(new SimpleGrantedAuthority("ROLE_" + account.role().name()));
    // Principal name is the opaque account id, never the raw external subject.
    return new JwtAuthenticationToken(jwt, authorities, account.id().value().toString());
  }
}
