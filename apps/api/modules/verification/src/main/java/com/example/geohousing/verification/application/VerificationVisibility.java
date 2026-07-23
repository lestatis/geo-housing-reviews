package com.example.geohousing.verification.application;

import com.example.geohousing.verification.domain.VerificationCase;
import com.example.geohousing.verification.domain.VerificationCaseId;
import com.example.geohousing.verification.domain.VerificationCaseNotFoundException;
import java.util.Optional;

/**
 * Who may see a single verification case. One rule, used by every path that resolves a case by id,
 * so an endpoint cannot accidentally skip the check.
 *
 * <p>A case is private (SECURITY_PRIVACY.md): visible only to the account it belongs to and to
 * moderators, and reported as <em>not found</em> to anyone else — a 403 would confirm that a
 * particular account is trying to verify a relationship to a particular property, which is exactly
 * what a private workflow must not reveal.
 */
final class VerificationVisibility {

  private VerificationVisibility() {}

  static VerificationCase requireVisible(
      Optional<VerificationCase> found, VerificationCaseId caseId, VerificationViewer viewer) {
    return found
        .filter(verificationCase -> isVisibleTo(verificationCase, viewer))
        .orElseThrow(() -> new VerificationCaseNotFoundException(caseId));
  }

  static boolean isVisibleTo(VerificationCase verificationCase, VerificationViewer viewer) {
    return viewer.moderator() || viewer.owns(verificationCase.accountRef());
  }
}
