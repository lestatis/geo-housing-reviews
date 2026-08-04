package com.example.geohousing.verification.infrastructure.persistence;

import com.example.geohousing.verification.api.VerificationMetrics;
import com.example.geohousing.verification.api.VerificationThroughput;
import com.example.geohousing.verification.domain.VerificationDecisionAction;
import com.example.geohousing.verification.domain.VerificationDecisionOutcome;
import com.example.geohousing.verification.domain.VerificationStatus;
import java.time.Instant;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verification's counts. Nothing here reaches the evidence, and nothing here should ever need to.
 */
@Repository
class JpaVerificationMetrics implements VerificationMetrics {

  private final SpringDataVerificationCaseRepository cases;
  private final SpringDataVerificationDecisionAuditEventRepository decisions;

  JpaVerificationMetrics(
      SpringDataVerificationCaseRepository cases,
      SpringDataVerificationDecisionAuditEventRepository decisions) {
    this.cases = cases;
    this.decisions = decisions;
  }

  @Override
  @Transactional(readOnly = true)
  public long pendingCases() {
    return cases.countByStatus(VerificationStatus.PENDING);
  }

  @Override
  @Transactional(readOnly = true)
  public VerificationThroughput between(Instant from, Instant until) {
    // Applied outcomes only: a decision recorded against a case that has gone approved nobody.
    return new VerificationThroughput(
        decisions.countActionBetween(
            VerificationDecisionAction.APPROVE, VerificationDecisionOutcome.APPLIED, from, until),
        decisions.countActionBetween(
            VerificationDecisionAction.REJECT, VerificationDecisionOutcome.APPLIED, from, until));
  }
}
