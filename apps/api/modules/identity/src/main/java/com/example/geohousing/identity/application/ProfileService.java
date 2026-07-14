package com.example.geohousing.identity.application;

import com.example.geohousing.identity.domain.Account;
import com.example.geohousing.identity.domain.AccountClosedException;
import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.AccountNotFoundException;
import com.example.geohousing.identity.domain.OptimisticLockConflictException;
import com.example.geohousing.identity.domain.Pseudonym;
import com.example.geohousing.identity.domain.PseudonymAlreadyInUseException;
import com.example.geohousing.identity.domain.PublicProfile;
import java.time.Clock;
import java.util.Objects;

/**
 * Reads and updates a user's public profile while preserving identity and concurrency invariants.
 */
public final class ProfileService {

  private final AccountRepository accountRepository;
  private final PublicProfileRepository publicProfileRepository;
  private final Clock clock;

  public ProfileService(
      AccountRepository accountRepository,
      PublicProfileRepository publicProfileRepository,
      Clock clock) {
    this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository");
    this.publicProfileRepository =
        Objects.requireNonNull(publicProfileRepository, "publicProfileRepository");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public PublicProfile getProfile(AccountId accountId) {
    return publicProfileRepository
        .findByAccountId(Objects.requireNonNull(accountId, "accountId"))
        .orElseThrow(() -> new AccountNotFoundException("public profile not found"));
  }

  public PublicProfile updateProfile(
      AccountId accountId,
      Pseudonym pseudonym,
      String avatarUrl,
      String locale,
      long expectedVersion) {
    Objects.requireNonNull(pseudonym, "pseudonym");
    Account account =
        accountRepository
            .findById(Objects.requireNonNull(accountId, "accountId"))
            .orElseThrow(() -> new AccountNotFoundException(accountId));
    if (account.isClosed()) {
      throw new AccountClosedException("closed accounts cannot update a public profile");
    }

    PublicProfile profile = getProfile(accountId);
    if (profile.version() != expectedVersion) {
      throw new OptimisticLockConflictException("public profile version does not match");
    }
    if (!profile.pseudonym().equals(pseudonym)
        && publicProfileRepository.isPseudonymInUse(pseudonym)) {
      throw new PseudonymAlreadyInUseException(pseudonym);
    }

    profile.updateProfile(pseudonym, avatarUrl, locale, clock);
    return publicProfileRepository.save(profile, expectedVersion);
  }
}
