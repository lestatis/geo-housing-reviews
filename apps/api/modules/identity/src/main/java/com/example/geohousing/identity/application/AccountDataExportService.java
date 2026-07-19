package com.example.geohousing.identity.application;

import com.example.geohousing.identity.domain.Account;
import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.AccountNotFoundException;
import com.example.geohousing.identity.domain.PublicProfile;
import com.example.geohousing.identity.domain.SelfServiceRequestType;
import java.util.Objects;

/** Returns a copy of the authenticated user's identity-local data, under an idempotency key. */
public final class AccountDataExportService {

  private final AccountRepository accountRepository;
  private final PublicProfileRepository publicProfileRepository;
  private final SelfServiceRequestRegistrar registrar;

  public AccountDataExportService(
      AccountRepository accountRepository,
      PublicProfileRepository publicProfileRepository,
      SelfServiceRequestRegistrar registrar) {
    this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository");
    this.publicProfileRepository =
        Objects.requireNonNull(publicProfileRepository, "publicProfileRepository");
    this.registrar = Objects.requireNonNull(registrar, "registrar");
  }

  public AccountExport export(AccountId accountId, String idempotencyKey) {
    Objects.requireNonNull(accountId, "accountId");
    registrar.register(accountId, idempotencyKey, SelfServiceRequestType.EXPORT);

    Account account =
        accountRepository
            .findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException(accountId));
    PublicProfile profile =
        publicProfileRepository
            .findByAccountId(accountId)
            .orElseThrow(() -> new AccountNotFoundException("public profile not found"));
    return AccountExport.from(account, profile);
  }
}
