package com.example.geohousing.identity.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataSelfServiceRequestRepository
    extends JpaRepository<SelfServiceRequestJpaEntity, UUID> {

  Optional<SelfServiceRequestJpaEntity> findByAccountIdAndIdempotencyKey(
      UUID accountId, String idempotencyKey);
}
