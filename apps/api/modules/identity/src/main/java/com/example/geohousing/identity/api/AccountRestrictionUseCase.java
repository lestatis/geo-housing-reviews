package com.example.geohousing.identity.api;

import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.RestrictionScope;
import com.example.geohousing.identity.domain.UserRestriction;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Placing and lifting restrictions on an account.
 *
 * <p>Published for the same reason as {@link AccountRoleUseCase}: the transaction belongs around
 * the whole use case, not around each repository call inside it. "At most one active restriction
 * per scope" is a claim about rows that must not change between reading them and writing one.
 */
public interface AccountRestrictionUseCase {

  UserRestriction restrict(
      AccountId moderatorId,
      AccountId targetId,
      RestrictionScope scope,
      String reason,
      Instant endAt);

  UserRestriction lift(AccountId moderatorId, AccountId accountId, UUID restrictionId);

  /**
   * Lifts by the restriction's opaque identifier, resolving the affected account inside identity.
   *
   * <p>This is for a cross-module workflow that records the ID it created. The administrator HTTP
   * endpoint deliberately continues to name both account and restriction, so a stale path cannot
   * silently end another account's restriction.
   */
  UserRestriction liftByRestrictionId(AccountId moderatorId, UUID restrictionId);

  List<UserRestriction> history(AccountId accountId);
}
