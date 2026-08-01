package com.example.geohousing.identity.infrastructure.persistence;

import com.example.geohousing.identity.domain.AccountRole;
import com.example.geohousing.identity.domain.AccountStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataAccountRepository extends JpaRepository<AccountJpaEntity, UUID> {

  Optional<AccountJpaEntity> findByAuthSubjectHash(String authSubjectHash);

  /**
   * Open accounts holding a role. Closed accounts are excluded because they cannot sign in to use
   * one — counting them would let the last usable administrator be demoted.
   */
  long countByRoleAndStatus(AccountRole role, AccountStatus status);
}
