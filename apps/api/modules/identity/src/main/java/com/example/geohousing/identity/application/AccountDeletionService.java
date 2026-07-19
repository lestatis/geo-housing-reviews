package com.example.geohousing.identity.application;

import com.example.geohousing.identity.domain.Account;
import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.AccountNotFoundException;
import com.example.geohousing.identity.domain.PublicProfile;
import com.example.geohousing.identity.domain.SelfServiceRequestType;
import java.time.Clock;
import java.util.Objects;

/**
 * Deletes the authenticated user's account: closes it (scrubbing email, retaining the
 * non-reversible auth-subject hash so the subject cannot be re-provisioned — ADR-0006) and
 * anonymizes the public profile. Idempotent — {@link Account#close} and {@link
 * PublicProfile#anonymize} are no-ops once applied — so the mutation runs on every call regardless
 * of whether the idempotency key is new.
 */
public final class AccountDeletionService {

  private final AccountRepository accountRepository;
  private final PublicProfileRepository publicProfileRepository;
  private final AccountDeletionRepository accountDeletionRepository;
  private final SelfServiceRequestRegistrar registrar;
  private final Clock clock;

  public AccountDeletionService(
      AccountRepository accountRepository,
      PublicProfileRepository publicProfileRepository,
      AccountDeletionRepository accountDeletionRepository,
      SelfServiceRequestRegistrar registrar,
      Clock clock) {
    this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository");
    this.publicProfileRepository =
        Objects.requireNonNull(publicProfileRepository, "publicProfileRepository");
    this.accountDeletionRepository =
        Objects.requireNonNull(accountDeletionRepository, "accountDeletionRepository");
    this.registrar = Objects.requireNonNull(registrar, "registrar");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public Account delete(AccountId accountId, String idempotencyKey) {
    Objects.requireNonNull(accountId, "accountId");
    registrar.register(accountId, idempotencyKey, SelfServiceRequestType.DELETE);

    Account account =
        accountRepository
            .findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException(accountId));
    PublicProfile profile =
        publicProfileRepository
            .findByAccountId(accountId)
            .orElseThrow(() -> new AccountNotFoundException("public profile not found"));

    account.close(clock);
    profile.anonymize(clock);
    accountDeletionRepository.applyDeletion(account, profile);
    return account;
  }
}
