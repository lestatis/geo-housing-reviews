package com.example.geohousing.verification.infrastructure.persistence;

import com.example.geohousing.verification.application.VerificationCaseRepository;
import com.example.geohousing.verification.application.VerificationDecisionRepository;
import com.example.geohousing.verification.domain.VerificationCase;
import com.example.geohousing.verification.domain.VerificationDecisionAuditEvent;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes a verification decision and its audit row in one transaction, so a case can never be
 * decided without the action being recorded.
 *
 * <p>The case is saved through the {@link VerificationCaseRepository} port rather than a second
 * write path: its adapter owns the optimistic-version check, and a decision must not become a way
 * around it. Its {@code @Transactional} joins this one, so the mutation and the audit row commit or
 * roll back together.
 */
@Repository
public class JpaVerificationDecisionRepository implements VerificationDecisionRepository {

  private final VerificationCaseRepository caseRepository;
  private final SpringDataVerificationDecisionAuditEventRepository auditEvents;

  public JpaVerificationDecisionRepository(
      VerificationCaseRepository caseRepository,
      SpringDataVerificationDecisionAuditEventRepository auditEvents) {
    this.caseRepository = caseRepository;
    this.auditEvents = auditEvents;
  }

  @Override
  @Transactional
  public void applyDecision(
      VerificationCase verificationCase, VerificationDecisionAuditEvent event) {
    caseRepository.save(verificationCase);
    auditEvents.saveAndFlush(VerificationDecisionAuditEventJpaMapper.toEntity(event));
  }

  @Override
  @Transactional
  public void recordAttempt(VerificationDecisionAuditEvent event) {
    auditEvents.saveAndFlush(VerificationDecisionAuditEventJpaMapper.toEntity(event));
  }
}
