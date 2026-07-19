package com.example.geohousing.identity.infrastructure;

import com.example.geohousing.identity.application.AccountProvisioningService;
import com.example.geohousing.identity.application.AccountRepository;
import com.example.geohousing.identity.application.AdminAccountLookupService;
import com.example.geohousing.identity.application.AuthSubjectHasher;
import com.example.geohousing.identity.application.IdentityProvisioningRepository;
import com.example.geohousing.identity.application.ProfileService;
import com.example.geohousing.identity.application.PseudonymAllocator;
import com.example.geohousing.identity.application.PublicProfileRepository;
import com.example.geohousing.identity.infrastructure.security.HmacAuthSubjectHasher;
import com.example.geohousing.identity.infrastructure.security.IdentityJwtAuthenticationConverter;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.HexFormat;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Constructs the identity module's framework-free application services as Spring beans. The
 * services themselves stay free of Spring and crypto (ArchUnit rules); this composition lives in
 * infrastructure. The three persistence ports are all satisfied by {@code
 * JpaIdentityPersistenceAdapter}, which is already a {@code @Repository}.
 */
@Configuration
@EnableConfigurationProperties(IdentitySecurityProperties.class)
public class IdentityBeanConfiguration {

  /** Bytes of entropy for a generated pseudonym suffix; 4 bytes render as 8 lowercase hex chars. */
  private static final int PSEUDONYM_SUFFIX_BYTES = 4;

  @Bean
  Clock identityClock() {
    return Clock.systemUTC();
  }

  @Bean
  AuthSubjectHasher authSubjectHasher(IdentitySecurityProperties properties) {
    return new HmacAuthSubjectHasher(properties.subjectPepper());
  }

  @Bean
  PseudonymAllocator pseudonymAllocator(PublicProfileRepository publicProfileRepository) {
    SecureRandom random = new SecureRandom();
    return new PseudonymAllocator(publicProfileRepository, () -> randomHexSuffix(random));
  }

  @Bean
  AccountProvisioningService accountProvisioningService(
      AccountRepository accountRepository,
      IdentityProvisioningRepository identityProvisioningRepository,
      AuthSubjectHasher authSubjectHasher,
      PseudonymAllocator pseudonymAllocator,
      Clock identityClock) {
    return new AccountProvisioningService(
        accountRepository,
        identityProvisioningRepository,
        authSubjectHasher,
        pseudonymAllocator,
        identityClock);
  }

  @Bean
  ProfileService profileService(
      AccountRepository accountRepository,
      PublicProfileRepository publicProfileRepository,
      Clock identityClock) {
    return new ProfileService(accountRepository, publicProfileRepository, identityClock);
  }

  @Bean
  AdminAccountLookupService adminAccountLookupService(AccountRepository accountRepository) {
    return new AdminAccountLookupService(accountRepository);
  }

  @Bean
  IdentityJwtAuthenticationConverter identityJwtAuthenticationConverter(
      AccountProvisioningService accountProvisioningService) {
    return new IdentityJwtAuthenticationConverter(accountProvisioningService);
  }

  private static String randomHexSuffix(SecureRandom random) {
    byte[] bytes = new byte[PSEUDONYM_SUFFIX_BYTES];
    random.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }
}
