package com.example.geohousing.identity.infrastructure.persistence;

import com.example.geohousing.identity.domain.Account;
import com.example.geohousing.identity.domain.AccountId;

final class AccountJpaMapper {

  private AccountJpaMapper() {}

  static AccountJpaEntity toEntity(Account account) {
    return new AccountJpaEntity(
        account.id().value(),
        account.authSubjectHash(),
        account.email().orElse(null),
        account.role(),
        account.status(),
        account.createdAt(),
        account.closedAt().orElse(null),
        account.version());
  }

  static Account toDomain(AccountJpaEntity entity) {
    return Account.reconstitute(
        AccountId.of(entity.id()),
        entity.authSubjectHash().strip(),
        entity.email(),
        entity.role(),
        entity.status(),
        entity.createdAt(),
        entity.closedAt(),
        entity.version());
  }
}
