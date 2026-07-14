package com.example.geohousing.identity.application;

import com.example.geohousing.identity.domain.Account;
import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.AccountNotFoundException;
import java.util.Objects;

/**
 * Application use case for later admin account lookup; authorization and auditing land in chunk 7.
 */
public final class AdminAccountLookupService {

  private final AccountRepository accountRepository;

  public AdminAccountLookupService(AccountRepository accountRepository) {
    this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository");
  }

  public Account findAccount(AccountId accountId) {
    return accountRepository
        .findById(Objects.requireNonNull(accountId, "accountId"))
        .orElseThrow(() -> new AccountNotFoundException(accountId));
  }
}
