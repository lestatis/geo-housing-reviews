package com.example.geohousing.identity.infrastructure.persistence;

import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.Pseudonym;
import com.example.geohousing.identity.domain.PublicProfile;

final class PublicProfileJpaMapper {

  private PublicProfileJpaMapper() {}

  static PublicProfileJpaEntity toEntity(PublicProfile profile) {
    return new PublicProfileJpaEntity(
        profile.accountId().value(),
        profile.pseudonym().value(),
        profile.avatarUrl().orElse(null),
        profile.locale(),
        profile.createdAt(),
        profile.updatedAt(),
        profile.version());
  }

  static PublicProfile toDomain(PublicProfileJpaEntity entity) {
    return PublicProfile.reconstitute(
        AccountId.of(entity.accountId()),
        Pseudonym.of(entity.pseudonym()),
        entity.avatarUrl(),
        entity.locale(),
        entity.createdAt(),
        entity.updatedAt(),
        entity.version());
  }

  static void copyMutableFields(PublicProfile profile, PublicProfileJpaEntity entity) {
    entity.apply(
        profile.pseudonym().value(),
        profile.avatarUrl().orElse(null),
        profile.locale(),
        profile.updatedAt());
  }
}
