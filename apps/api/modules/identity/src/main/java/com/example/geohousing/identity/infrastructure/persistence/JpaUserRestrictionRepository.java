package com.example.geohousing.identity.infrastructure.persistence;

import com.example.geohousing.identity.application.UserRestrictionRepository;
import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.UserRestriction;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JPA implementation of the {@link UserRestrictionRepository} read port. */
@Repository
public class JpaUserRestrictionRepository implements UserRestrictionRepository {

  private final SpringDataUserRestrictionRepository restrictions;

  public JpaUserRestrictionRepository(SpringDataUserRestrictionRepository restrictions) {
    this.restrictions = restrictions;
  }

  @Override
  @Transactional(readOnly = true)
  public List<UserRestriction> findActiveRestrictions(AccountId accountId, Instant asOf) {
    return restrictions.findActive(accountId.value(), asOf).stream()
        .map(UserRestrictionJpaMapper::toDomain)
        .toList();
  }
}
