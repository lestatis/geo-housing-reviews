package com.example.geohousing.verification.application;

import com.example.geohousing.verification.domain.VerificationCaseId;

/**
 * Raised when the caller may see a case but may not perform this action on it — cancelling a case
 * that belongs to another account, for instance. Cases the caller is not even allowed to know about
 * are reported as not found instead, so this never confirms the existence of a private case.
 */
public class VerificationAccessDeniedException extends RuntimeException {

  public VerificationAccessDeniedException(VerificationCaseId caseId) {
    super("not allowed to act on verification case " + caseId.value());
  }

  private VerificationAccessDeniedException(String message) {
    super(message);
  }

  /** The moderator queue is moderators-only; used as defence in depth behind the role gate. */
  public static VerificationAccessDeniedException moderatorOnly() {
    return new VerificationAccessDeniedException("this action is restricted to moderators");
  }
}
