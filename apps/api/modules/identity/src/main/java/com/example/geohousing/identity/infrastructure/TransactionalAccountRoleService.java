package com.example.geohousing.identity.infrastructure;

import com.example.geohousing.identity.api.AccountRoleUseCase;
import com.example.geohousing.identity.application.AccountRoleService;
import com.example.geohousing.identity.domain.Account;
import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.AccountRole;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction boundary for the role use case.
 *
 * <p>{@code .claude/rules/backend-java.md} asks for boundaries at application use cases rather than
 * at repository calls, and this is where that is honoured. Everything the service does — locking
 * the administrators, counting them, saving the target, recording the audit row — commits or rolls
 * back together.
 *
 * <p>Two defects lived in the gap this closes. Without one transaction the lock was released before
 * the save, so two simultaneous demotions could each pass the last-administrator check and leave
 * the platform unreachable. And a failed audit write returned an error <em>after</em> the role
 * change had committed, producing exactly the unrecorded grant the audit log exists to make
 * impossible.
 *
 * <p>A decorator rather than an annotation on {@link AccountRoleService}, because the application
 * layer stays framework-free: the rules live there, the transaction lives here.
 */
public class TransactionalAccountRoleService implements AccountRoleUseCase {

  private final AccountRoleService delegate;

  public TransactionalAccountRoleService(AccountRoleService delegate) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
  }

  @Override
  @Transactional
  public Account changeRole(
      AccountId adminAccountId,
      AccountId targetAccountId,
      AccountRole newRole,
      long expectedVersion) {
    return delegate.changeRole(adminAccountId, targetAccountId, newRole, expectedVersion);
  }
}
