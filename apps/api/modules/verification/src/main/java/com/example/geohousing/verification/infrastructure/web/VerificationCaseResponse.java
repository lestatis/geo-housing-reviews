package com.example.geohousing.verification.infrastructure.web;

import com.example.geohousing.verification.domain.VerificationBadge;
import com.example.geohousing.verification.domain.VerificationCase;
import java.time.Instant;

/**
 * View of a verification case for its owner or a moderator.
 *
 * <p>{@code decidedBy} is deliberately omitted: which moderator decided a case is internal, and a
 * user does not need another account's id to understand the outcome. The {@code badge} is the
 * public-safe projection and carries no document/apartment/address — only a label, timestamps and
 * the explanation key.
 */
public record VerificationCaseResponse(
    String caseId,
    String accountId,
    String propertyId,
    String relationshipClaim,
    String method,
    String status,
    String tier,
    String decisionReasonCode,
    BadgeView badge,
    Instant verifiedAt,
    Instant validThrough,
    Instant createdAt,
    Instant updatedAt,
    long version) {

  public record BadgeView(
      String type, Instant verifiedAt, Instant validThrough, String explanationKey) {}

  static VerificationCaseResponse from(VerificationCase verificationCase) {
    return new VerificationCaseResponse(
        verificationCase.id().value().toString(),
        verificationCase.accountRef().value().toString(),
        verificationCase.propertyRef().value().toString(),
        verificationCase.relationshipClaim().name(),
        verificationCase.method().name(),
        verificationCase.status().name(),
        verificationCase.tier().name(),
        verificationCase.decisionReasonCode().orElse(null),
        verificationCase.badge().map(VerificationCaseResponse::toBadgeView).orElse(null),
        verificationCase.verifiedAt().orElse(null),
        verificationCase.validThrough().orElse(null),
        verificationCase.createdAt(),
        verificationCase.updatedAt(),
        verificationCase.version());
  }

  private static BadgeView toBadgeView(VerificationBadge badge) {
    return new BadgeView(
        badge.type().name(),
        badge.verifiedAt(),
        badge.expiry().orElse(null),
        badge.explanationKey());
  }
}
