package com.example.geohousing.identity.infrastructure.persistence;

import com.example.geohousing.identity.application.AdminAuditEventRepository;
import com.example.geohousing.identity.domain.AdminAuditEvent;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** JPA implementation of the append-only {@link AdminAuditEventRepository}. */
@Repository
public class JpaAdminAuditEventRepository implements AdminAuditEventRepository {

  private final SpringDataAdminAuditEventRepository events;

  public JpaAdminAuditEventRepository(SpringDataAdminAuditEventRepository events) {
    this.events = events;
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordRefusedAttempt(AdminAuditEvent event) {
    // A transaction of its own, so the row outlives the rollback the refusal is about to cause.
    record(event);
  }

  @Override
  @Transactional
  public void record(AdminAuditEvent event) {
    events.save(AdminAuditEventJpaMapper.toEntity(event));
  }
}
