package com.example.geohousing.identity.infrastructure.persistence;

import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.SelfServiceRequest;

final class SelfServiceRequestJpaMapper {

  private SelfServiceRequestJpaMapper() {}

  static SelfServiceRequestJpaEntity toEntity(SelfServiceRequest request) {
    return new SelfServiceRequestJpaEntity(
        request.id(),
        request.accountId().value(),
        request.idempotencyKey(),
        request.type(),
        request.createdAt());
  }

  static SelfServiceRequest toDomain(SelfServiceRequestJpaEntity entity) {
    return SelfServiceRequest.reconstitute(
        entity.id(),
        AccountId.of(entity.accountId()),
        entity.idempotencyKey(),
        entity.requestType(),
        entity.createdAt());
  }
}
