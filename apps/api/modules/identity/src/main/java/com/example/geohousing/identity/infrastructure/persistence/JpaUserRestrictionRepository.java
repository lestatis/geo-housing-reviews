package com.example.geohousing.identity.infrastructure.persistence;

import com.example.geohousing.identity.application.UserRestrictionRepository;
import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.UserRestriction;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JPA implementation of the {@link UserRestrictionRepository} port. */
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

  @Override
  @Transactional(readOnly = true)
  public List<UserRestriction> findAllFor(AccountId accountId) {
    return restrictions.findByAccountIdOrderByStartAtDesc(accountId.value()).stream()
        .map(UserRestrictionJpaMapper::toDomain)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UserRestriction> findById(UUID restrictionId) {
    return restrictions.findById(restrictionId).map(UserRestrictionJpaMapper::toDomain);
  }

  @Override
  @Transactional
  public void create(UserRestriction restriction) {
    restrictions.save(UserRestrictionJpaMapper.toEntity(restriction));
  }

  @Override
  @Transactional
  public void save(UserRestriction restriction) {
    // The row is rewritten rather than removed: only the end of the window moves, and a lifted
    // restriction stays readable because an appeal needs to see that it happened.
    restrictions.save(UserRestrictionJpaMapper.toEntity(restriction));
  }
}
