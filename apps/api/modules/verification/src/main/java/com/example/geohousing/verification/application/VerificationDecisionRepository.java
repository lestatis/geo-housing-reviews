package com.example.geohousing.verification.application;

import com.example.geohousing.verification.domain.VerificationCase;
import com.example.geohousing.verification.domain.VerificationDecisionAuditEvent;

/**
 * Persistence boundary for verification decisions. The state change and its audit row are written
 * together so a case can never be decided without the action being recorded — the application layer
 * stays framework-free, so the atomicity lives in the adapter rather than a transactional service.
 */
public interface VerificationDecisionRepository {

  /**
   * Persists the decided case and the audit event in one transaction.
   *
   * @throws com.example.geohousing.verification.domain.VerificationVersionConflictException if the
   *     stored version no longer matches the one the case was loaded at
   */
  void applyDecision(VerificationCase verificationCase, VerificationDecisionAuditEvent event);

  /** Records a decision attempted against a case that does not exist. */
  void recordAttempt(VerificationDecisionAuditEvent event);
}
