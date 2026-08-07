package com.example.geohousing.identity.api;

import com.example.geohousing.identity.domain.Account;
import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.AccountRole;

/**
 * Granting and removing administrative access.
 *
 * <p>Published as an interface because the transaction boundary belongs around the whole use case,
 * not around each repository call it makes ({@code .claude/rules/backend-java.md}). The application
 * service holds the rules and stays framework-free; an infrastructure decorator supplies the
 * transaction, and callers depend on this rather than on either.
 */
public interface AccountRoleUseCase {

  /**
   * Changes an account's role, refusing to remove the last administrator.
   *
   * <p>That refusal is only true if the count and the save happen together. They did not, once: two
   * administrators demoting each other simultaneously both counted two, both succeeded, and the
   * platform was left with nobody able to grant the role back.
   *
   * @throws com.example.geohousing.identity.application.LastAdministratorException if this would
   *     leave no administrator
   * @throws com.example.geohousing.identity.domain.OptimisticLockConflictException if the account
   *     changed since it was read
   */
  Account changeRole(
      AccountId adminAccountId,
      AccountId targetAccountId,
      AccountRole newRole,
      long expectedVersion);
}
