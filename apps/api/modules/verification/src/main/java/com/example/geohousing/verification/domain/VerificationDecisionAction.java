package com.example.geohousing.verification.domain;

/**
 * A moderation action on a verification case. Mirrors the {@code action} check constraint on {@code
 * verification.verification_decision_audit_event}. {@code EXPIRE} is system-initiated; the rest are
 * performed by a person.
 */
public enum VerificationDecisionAction {
  APPROVE,
  REJECT,
  EXPIRE,
  CANCEL,
  REVOKE
}
