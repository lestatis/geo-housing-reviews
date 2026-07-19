package com.example.geohousing.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.geohousing.identity.application.AccountProvisioningService;
import com.example.geohousing.identity.application.AccountRepository;
import com.example.geohousing.identity.application.IdentityProvisioningRepository;
import com.example.geohousing.identity.application.PseudonymAllocator;
import com.example.geohousing.identity.application.PublicProfileRepository;
import com.example.geohousing.identity.domain.Account;
import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.AccountRole;
import com.example.geohousing.identity.domain.AccountStatus;
import com.example.geohousing.identity.domain.Pseudonym;
import com.example.geohousing.identity.domain.PublicProfile;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

class IdentityJwtAuthenticationConverterTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneOffset.UTC);

  @Test
  void derivesRoleFromTheAccountAndIgnoresSpoofedClaims() {
    Account userAccount = accountWithRole(AccountRole.USER);
    IdentityJwtAuthenticationConverter converter = converterReturning(userAccount);
    Jwt jwt =
        jwt(
            "issuer|subject",
            Map.of(
                "roles", List.of("ADMIN"), "scope", "admin", "authorities", List.of("ROLE_ADMIN")));

    AbstractAuthenticationToken token = converter.convert(jwt);

    assertThat(token.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_USER");
  }

  @Test
  void grantsAdminOnlyWhenTheAccountRoleIsAdmin() {
    IdentityJwtAuthenticationConverter converter =
        converterReturning(accountWithRole(AccountRole.ADMIN));

    AbstractAuthenticationToken token = converter.convert(jwt("issuer|admin", Map.of()));

    assertThat(token.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_ADMIN");
  }

  @Test
  void usesTheOpaqueAccountIdAsThePrincipalNameNotTheRawSubject() {
    Account account = accountWithRole(AccountRole.USER);
    IdentityJwtAuthenticationConverter converter = converterReturning(account);

    AbstractAuthenticationToken token = converter.convert(jwt("issuer|subject", Map.of()));

    assertThat(token.getName()).isEqualTo(account.id().value().toString());
    assertThat(token.getName()).isNotEqualTo("issuer|subject");
  }

  @Test
  void autoProvisionsAnUnknownSubjectOnFirstRequest() {
    RecordingProvisioningRepository provisioning = new RecordingProvisioningRepository();
    IdentityJwtAuthenticationConverter converter = converterAutoProvisioning(provisioning);

    AbstractAuthenticationToken token = converter.convert(jwt("issuer|newcomer", Map.of()));

    assertThat(provisioning.created).isNotNull();
    assertThat(token.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_USER");
    assertThat(token.getName()).isEqualTo(provisioning.created.id().value().toString());
  }

  @Test
  void rejectsAClosedAccountAsAnAuthenticationFailure() {
    Account closed = accountWithRole(AccountRole.USER);
    closed.close(CLOCK);
    IdentityJwtAuthenticationConverter converter = converterReturning(closed);

    assertThatThrownBy(() -> converter.convert(jwt("issuer|deleted", Map.of())))
        .isInstanceOf(DisabledException.class);
  }

  @Test
  void rejectsATokenWithoutASubjectClaim() {
    IdentityJwtAuthenticationConverter converter =
        converterReturning(accountWithRole(AccountRole.USER));
    Jwt noSubject = Jwt.withTokenValue("t").header("alg", "none").claim("email", "x@y.z").build();

    assertThatThrownBy(() -> converter.convert(noSubject))
        .isInstanceOf(InvalidBearerTokenException.class);
  }

  private static Account accountWithRole(AccountRole role) {
    return Account.reconstitute(
        AccountId.of(UUID.randomUUID()),
        "a".repeat(64),
        null,
        role,
        AccountStatus.ACTIVE,
        CLOCK.instant(),
        null,
        0L);
  }

  private static Jwt jwt(String subject, Map<String, Object> claims) {
    Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "none").subject(subject);
    claims.forEach(builder::claim);
    return builder.build();
  }

  /** Converter over a provisioning service that always resolves the given existing account. */
  private static IdentityJwtAuthenticationConverter converterReturning(Account existing) {
    AccountRepository accounts = new SeededAccountRepository(existing);
    return new IdentityJwtAuthenticationConverter(
        new AccountProvisioningService(
            accounts,
            new RecordingProvisioningRepository(),
            rawSubject -> "hash",
            new PseudonymAllocator(new EmptyProfileRepository(), () -> "abcd"),
            CLOCK));
  }

  /** Converter over a provisioning service with no existing account, so the subject is created. */
  private static IdentityJwtAuthenticationConverter converterAutoProvisioning(
      RecordingProvisioningRepository provisioning) {
    return new IdentityJwtAuthenticationConverter(
        new AccountProvisioningService(
            new SeededAccountRepository(null),
            provisioning,
            rawSubject -> "hash",
            new PseudonymAllocator(new EmptyProfileRepository(), () -> "abcd"),
            CLOCK));
  }

  private static final class SeededAccountRepository implements AccountRepository {

    private final Account seeded;

    private SeededAccountRepository(Account seeded) {
      this.seeded = seeded;
    }

    @Override
    public Optional<Account> findById(AccountId accountId) {
      return Optional.empty();
    }

    @Override
    public Optional<Account> findByAuthSubjectHash(String authSubjectHash) {
      return Optional.ofNullable(seeded);
    }
  }

  private static final class EmptyProfileRepository implements PublicProfileRepository {

    @Override
    public Optional<PublicProfile> findByAccountId(AccountId accountId) {
      return Optional.empty();
    }

    @Override
    public boolean isPseudonymInUse(Pseudonym pseudonym) {
      return false;
    }

    @Override
    public PublicProfile save(PublicProfile profile, long expectedVersion) {
      return profile;
    }
  }

  private static final class RecordingProvisioningRepository
      implements IdentityProvisioningRepository {

    private Account created;

    @Override
    public void create(Account account, PublicProfile profile) {
      this.created = account;
    }
  }
}
