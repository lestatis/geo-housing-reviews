package com.example.geohousing.identity.infrastructure.persistence;

import com.example.geohousing.identity.domain.AccountRole;
import com.example.geohousing.identity.domain.AccountStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SpringDataAccountRepository extends JpaRepository<AccountJpaEntity, UUID> {

  Optional<AccountJpaEntity> findByAuthSubjectHash(String authSubjectHash);

  /**
   * Open accounts holding a role. Closed accounts are excluded because they cannot sign in to use
   * one — counting them would let the last usable administrator be demoted.
   */
  long countByRoleAndStatus(AccountRole role, AccountStatus status);

  /**
   * Locks every active administrator for the duration of the transaction.
   *
   * <p>The rows are not read for their contents — the lock is the point. Anything deciding "is this
   * the last administrator" must hold it, or two callers answer the question from two snapshots and
   * both act on the answer.
   *
   * <p><strong>NO KEY UPDATE, not UPDATE.</strong> It still serializes role changes, which is all
   * the rule needs, but it permits the {@code FOR KEY SHARE} that a foreign-key check takes — and
   * {@code admin_audit_event.admin_account_id} references this table. Plain {@code FOR UPDATE}
   * blocked the audit insert that records a refusal, while the transaction holding the lock waited
   * for that insert to return. PostgreSQL cannot see that cycle, because half of it is a thread
   * waiting rather than a session waiting, so nothing detected a deadlock and the request simply
   * hung. The role column is part of no key, so weakening the lock costs nothing.
   */
  @Query(
      value =
          "select id from identity.account where role = 'ADMIN' and status = 'ACTIVE'"
              + " for no key update",
      nativeQuery = true)
  List<UUID> lockActiveAdministrators();

  /** Locks one account for the transaction, permitting the key-share a foreign key check takes. */
  @Query(
      value = "select id from identity.account where id = :accountId for no key update",
      nativeQuery = true)
  List<UUID> lockAccount(@Param("accountId") UUID accountId);
}
