package com.example.geohousing.identity.application;

import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.UserRestriction;
import java.time.Instant;
import java.util.List;

/** Application port for reading the restrictions placed on an account. */
public interface UserRestrictionRepository {

  /**
   * Restrictions for the account whose window could contain {@code asOf}. Implementations narrow to
   * candidates at the database (an index-friendly {@code start_at <= asOf} filter); the domain's
   * {@link UserRestriction#isActiveAt(Instant)} remains the authoritative check on the caller side.
   */
  List<UserRestriction> findActiveRestrictions(AccountId accountId, Instant asOf);
}
