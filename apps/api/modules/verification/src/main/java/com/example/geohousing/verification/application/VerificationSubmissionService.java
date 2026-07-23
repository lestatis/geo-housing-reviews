package com.example.geohousing.verification.application;

import com.example.geohousing.verification.domain.PropertyRef;
import com.example.geohousing.verification.domain.VerificationCase;
import com.example.geohousing.verification.domain.VerificationCaseId;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/**
 * Opens and withdraws verification cases on behalf of accounts.
 *
 * <p>Opening a case does not grant anything — it goes to {@code PENDING} for a moderator to decide.
 * An account holds one live case (pending or approved) per property; a second attempt is refused
 * and points at the existing case.
 */
public final class VerificationSubmissionService {

  /** The policy version stamped on cases opened under the current rules. */
  static final int CURRENT_POLICY_VERSION = 1;

  private final VerificationCaseRepository caseRepository;
  private final PropertyLookup propertyLookup;
  private final Clock clock;

  public VerificationSubmissionService(
      VerificationCaseRepository caseRepository, PropertyLookup propertyLookup, Clock clock) {
    this.caseRepository = Objects.requireNonNull(caseRepository, "caseRepository");
    this.propertyLookup = Objects.requireNonNull(propertyLookup, "propertyLookup");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * Opens a pending case for a moderator to decide.
   *
   * @throws PropertyNotFoundForVerificationException if the property does not exist
   * @throws DuplicateVerificationCaseException if the account already has a live case for it
   */
  public VerificationCase open(OpenVerificationCommand command) {
    Objects.requireNonNull(command, "command");

    PropertyRef target =
        propertyLookup
            .resolve(command.propertyRef())
            .orElseThrow(() -> new PropertyNotFoundForVerificationException(command.propertyRef()));

    caseRepository
        .findLiveByAccountAndProperty(command.accountRef(), target)
        .ifPresent(
            existing -> {
              throw new DuplicateVerificationCaseException(target, existing.id());
            });

    VerificationCase verificationCase =
        VerificationCase.open(
            VerificationCaseId.of(UUID.randomUUID()),
            command.accountRef(),
            target,
            command.relationshipClaim(),
            command.method(),
            CURRENT_POLICY_VERSION,
            clock);
    caseRepository.create(verificationCase);
    return verificationCase;
  }

  /**
   * Withdraws a pending case at its owner's request.
   *
   * @throws com.example.geohousing.verification.domain.VerificationCaseNotFoundException if the
   *     case does not exist or the caller may not see it
   * @throws VerificationAccessDeniedException if the caller is not the owner
   * @throws com.example.geohousing.verification.domain.IllegalVerificationStateTransitionException
   *     if the case is not pending
   */
  public VerificationCase cancel(VerificationCaseId caseId, VerificationViewer viewer) {
    Objects.requireNonNull(caseId, "caseId");
    Objects.requireNonNull(viewer, "viewer");

    VerificationCase verificationCase =
        VerificationVisibility.requireVisible(caseRepository.findById(caseId), caseId, viewer);
    // Only the owner cancels — a moderator withdrawing a case is a REJECT, which is audited.
    if (!viewer.owns(verificationCase.accountRef())) {
      throw new VerificationAccessDeniedException(caseId);
    }
    verificationCase.cancel(clock);
    caseRepository.save(verificationCase);
    return verificationCase;
  }
}
