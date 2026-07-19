package com.example.geohousing.identity.application;

import com.example.geohousing.identity.domain.Account;
import com.example.geohousing.identity.domain.PublicProfile;
import java.time.Instant;

/**
 * A user's identity-local data, returned to the user themselves. Unlike the public profile or the
 * admin view, this deliberately <em>includes</em> email — it is the account owner's own data and
 * the point of an export. The auth-subject hash is still excluded: it is a non-reversible
 * credential artefact, not user-facing data.
 */
public record AccountExport(ExportedAccount account, ExportedProfile profile) {

  public record ExportedAccount(
      String accountId,
      String email,
      String role,
      String status,
      Instant createdAt,
      Instant closedAt) {}

  public record ExportedProfile(
      String pseudonym, String avatarUrl, String locale, Instant createdAt, Instant updatedAt) {}

  static AccountExport from(Account account, PublicProfile profile) {
    return new AccountExport(
        new ExportedAccount(
            account.id().value().toString(),
            account.email().orElse(null),
            account.role().name(),
            account.status().name(),
            account.createdAt(),
            account.closedAt().orElse(null)),
        new ExportedProfile(
            profile.pseudonym().value(),
            profile.avatarUrl().orElse(null),
            profile.locale(),
            profile.createdAt(),
            profile.updatedAt()));
  }
}
