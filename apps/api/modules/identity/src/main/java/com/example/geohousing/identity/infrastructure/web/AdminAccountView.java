package com.example.geohousing.identity.infrastructure.web;

import com.example.geohousing.identity.domain.Account;
import java.time.Instant;

/**
 * Admin-facing view of an account. Deliberately excludes {@code authSubjectHash} (a confidential
 * credential-derived value that must never leave the server) and email; it exposes only the account
 * lifecycle metadata an admin needs. Admin access to this view is audited.
 *
 * <p>{@code version} is here because a role change must carry the version the administrator saw,
 * and this is where they see it — without it the endpoint would demand a number no caller could
 * obtain.
 */
public record AdminAccountView(
    String accountId,
    String role,
    String status,
    Instant createdAt,
    Instant closedAt,
    long version) {

  static AdminAccountView from(Account account) {
    return new AdminAccountView(
        account.id().value().toString(),
        account.role().name(),
        account.status().name(),
        account.createdAt(),
        account.closedAt().orElse(null),
        account.version());
  }
}
