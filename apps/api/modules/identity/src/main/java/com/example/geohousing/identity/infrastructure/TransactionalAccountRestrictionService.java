package com.example.geohousing.identity.infrastructure;

import com.example.geohousing.identity.api.AccountRestrictionUseCase;
import com.example.geohousing.identity.application.AccountRestrictionService;
import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.RestrictionScope;
import com.example.geohousing.identity.domain.UserRestriction;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction boundary for restriction use cases.
 *
 * <p>Same shape and same reasons as {@link TransactionalAccountRoleService}: the lock the service
 * takes is worth nothing unless it is held until the write commits, and a mutation and its audit
 * row must succeed or fail as one. Refusals still escape it — they are recorded through the port's
 * independent path, because a refusal ends by throwing and would otherwise erase its own record.
 */
public class TransactionalAccountRestrictionService implements AccountRestrictionUseCase {

  private final AccountRestrictionService delegate;

  public TransactionalAccountRestrictionService(AccountRestrictionService delegate) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
  }

  @Override
  @Transactional
  public UserRestriction restrict(
      AccountId moderatorId,
      AccountId targetId,
      RestrictionScope scope,
      String reason,
      Instant endAt) {
    return delegate.restrict(moderatorId, targetId, scope, reason, endAt);
  }

  @Override
  @Transactional
  public UserRestriction lift(AccountId moderatorId, AccountId accountId, UUID restrictionId) {
    return delegate.lift(moderatorId, accountId, restrictionId);
  }

  @Override
  @Transactional(readOnly = true)
  public List<UserRestriction> history(AccountId accountId) {
    return delegate.history(accountId);
  }
}
