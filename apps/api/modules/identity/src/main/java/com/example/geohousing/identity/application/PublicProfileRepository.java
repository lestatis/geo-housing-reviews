package com.example.geohousing.identity.application;

import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.Pseudonym;
import com.example.geohousing.identity.domain.PublicProfile;
import java.util.Optional;

/** Application port for public-profile lookup, uniqueness checks, and optimistic updates. */
public interface PublicProfileRepository {

  Optional<PublicProfile> findByAccountId(AccountId accountId);

  boolean isPseudonymInUse(Pseudonym pseudonym);

  /** Persists an edit only when the stored profile still has {@code expectedVersion}. */
  PublicProfile save(PublicProfile profile, long expectedVersion);
}
