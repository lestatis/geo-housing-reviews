package com.example.geohousing.identity.application;

import com.example.geohousing.identity.domain.Account;
import com.example.geohousing.identity.domain.AccountClosedException;
import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.PublicProfile;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/** Creates an account and default public profile on the first authenticated request. */
public final class AccountProvisioningService {

  private final AccountRepository accountRepository;
  private final IdentityProvisioningRepository identityProvisioningRepository;
  private final AuthSubjectHasher authSubjectHasher;
  private final PseudonymAllocator pseudonymAllocator;
  private final Clock clock;

  public AccountProvisioningService(
      AccountRepository accountRepository,
      IdentityProvisioningRepository identityProvisioningRepository,
      AuthSubjectHasher authSubjectHasher,
      PseudonymAllocator pseudonymAllocator,
      Clock clock) {
    this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository");
    this.identityProvisioningRepository =
        Objects.requireNonNull(identityProvisioningRepository, "identityProvisioningRepository");
    this.authSubjectHasher = Objects.requireNonNull(authSubjectHasher, "authSubjectHasher");
    this.pseudonymAllocator = Objects.requireNonNull(pseudonymAllocator, "pseudonymAllocator");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * Finds an existing active account or atomically creates an account/profile pair. The raw auth
   * subject is passed only to the hasher and is never sent to a repository or persisted object.
   */
  public Account provision(String rawAuthSubject, String email, String locale) {
    String authSubjectHash = authSubjectHasher.hash(requireText(rawAuthSubject, "rawAuthSubject"));
    if (authSubjectHash == null || authSubjectHash.isBlank()) {
      throw new IllegalArgumentException("authSubjectHasher returned a blank hash");
    }

    Account existing = accountRepository.findByAuthSubjectHash(authSubjectHash).orElse(null);
    if (existing != null) {
      if (existing.isClosed()) {
        throw new AccountClosedException("closed accounts cannot be re-provisioned");
      }
      return existing;
    }

    Account account =
        Account.provision(AccountId.of(UUID.randomUUID()), authSubjectHash, email, clock);
    PublicProfile profile =
        PublicProfile.createDefault(
            account.id(), pseudonymAllocator.allocateDefault(), locale, clock);
    identityProvisioningRepository.create(account, profile);
    return account;
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value;
  }
}
