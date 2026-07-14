package com.example.geohousing.identity.infrastructure.persistence;

import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.UserRestriction;

final class UserRestrictionJpaMapper {

  private UserRestrictionJpaMapper() {}

  static UserRestrictionJpaEntity toEntity(UserRestriction restriction) {
    return new UserRestrictionJpaEntity(
        restriction.id(),
        restriction.accountId().value(),
        restriction.scope(),
        restriction.reason(),
        restriction.startAt(),
        restriction.endAt().orElse(null),
        restriction.moderatorAccountId().map(AccountId::value).orElse(null),
        restriction.appealStatus(),
        restriction.createdAt());
  }

  static UserRestriction toDomain(UserRestrictionJpaEntity entity) {
    return UserRestriction.reconstitute(
        entity.id(),
        AccountId.of(entity.accountId()),
        entity.scope(),
        entity.reason(),
        entity.startAt(),
        entity.endAt(),
        entity.moderatorAccountId() == null ? null : AccountId.of(entity.moderatorAccountId()),
        entity.appealStatus(),
        entity.createdAt());
  }
}
