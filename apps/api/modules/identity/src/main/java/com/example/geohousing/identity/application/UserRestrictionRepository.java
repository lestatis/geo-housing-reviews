package com.example.geohousing.identity.application;

import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.UserRestriction;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Application port for the restrictions placed on an account. */
public interface UserRestrictionRepository {

  /**
   * Restrictions for the account whose window could contain {@code asOf}. Implementations narrow to
   * candidates at the database (an index-friendly {@code start_at <= asOf} filter); the domain's
   * {@link UserRestriction#isActiveAt(Instant)} remains the authoritative check on the caller side.
   */
  List<UserRestriction> findActiveRestrictions(AccountId accountId, Instant asOf);

  /**
   * Every restriction ever placed on this account, newest first — a moderator reads the history.
   */
  List<UserRestriction> findAllFor(AccountId accountId);

  Optional<UserRestriction> findById(UUID restrictionId);

  void create(UserRestriction restriction);

  /**
   * Persists a lifted restriction. Only the end of the window moves; the row is never removed,
   * because a lifted restriction is history rather than a mistake to erase.
   */
  void save(UserRestriction restriction);
}
