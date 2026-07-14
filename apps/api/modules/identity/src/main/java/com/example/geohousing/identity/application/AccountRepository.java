package com.example.geohousing.identity.application;

import com.example.geohousing.identity.domain.Account;
import com.example.geohousing.identity.domain.AccountId;
import java.util.Optional;

/** Application port for looking up private accounts without exposing persistence details. */
public interface AccountRepository {

  Optional<Account> findById(AccountId accountId);

  /** Looks up only the opaque, already-hashed external subject (never a raw OIDC subject). */
  Optional<Account> findByAuthSubjectHash(String authSubjectHash);
}
