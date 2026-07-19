package com.example.geohousing.identity.infrastructure.web;

import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.PublicProfile;

/**
 * Self-view of the authenticated user. Deliberately omits email and any legal identity — this is
 * the account owner's own public projection plus the fields a client needs to render and edit it
 * ({@code role} for admin affordances, {@code version} for optimistic concurrency).
 */
public record MeResponse(
    String accountId,
    String role,
    String pseudonym,
    String avatarUrl,
    String locale,
    long version) {

  static MeResponse from(AccountId accountId, String role, PublicProfile profile) {
    return new MeResponse(
        accountId.value().toString(),
        role,
        profile.pseudonym().value(),
        profile.avatarUrl().orElse(null),
        profile.locale(),
        profile.version());
  }
}
