package com.example.geohousing.identity.infrastructure.persistence;

import com.example.geohousing.identity.domain.AccountRole;
import com.example.geohousing.identity.domain.AccountStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
   */
  @Query(
      value =
          "select id from identity.account where role = 'ADMIN' and status = 'ACTIVE' for update",
      nativeQuery = true)
  List<UUID> lockActiveAdministrators();
}
