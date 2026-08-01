package com.example.geohousing.identity.application;

import com.example.geohousing.identity.domain.Account;
import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.AccountRole;
import java.util.Optional;

/** Application port for looking up private accounts without exposing persistence details. */
public interface AccountRepository {

  Optional<Account> findById(AccountId accountId);

  /** Looks up only the opaque, already-hashed external subject (never a raw OIDC subject). */
  Optional<Account> findByAuthSubjectHash(String authSubjectHash);

  /**
   * Persists a change only when the stored account still has {@code expectedVersion}, mirroring
   * {@link PublicProfileRepository#save}. Two administrators reading the same account is ordinary;
   * without this the second silently overwrites the first.
   */
  Account save(Account account, long expectedVersion);

  /**
   * How many open accounts currently hold this role. Backs the rule that the last administrator
   * cannot be demoted — closed accounts do not count, because they cannot sign in to use it.
   */
  long countByRole(AccountRole role);
}
