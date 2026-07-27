package com.example.geohousing.verification.infrastructure.persistence;

import com.example.geohousing.verification.application.EvidenceAccessAuditRepository;
import com.example.geohousing.verification.domain.EvidenceAccessEvent;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists the evidence access audit. Flushed immediately: the application writes the audit row
 * before disclosing the bytes, so it must reach the database rather than sit in a persistence
 * context that a later failure could discard.
 */
@Repository
public class JpaEvidenceAccessAuditRepository implements EvidenceAccessAuditRepository {

  private final SpringDataEvidenceAccessEventRepository accessEvents;

  public JpaEvidenceAccessAuditRepository(SpringDataEvidenceAccessEventRepository accessEvents) {
    this.accessEvents = accessEvents;
  }

  @Override
  @Transactional
  public void record(EvidenceAccessEvent event) {
    accessEvents.saveAndFlush(EvidenceAccessEventJpaMapper.toEntity(event));
  }
}
