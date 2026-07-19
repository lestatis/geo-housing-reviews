package com.example.geohousing.identity.infrastructure.web;

import com.example.geohousing.identity.domain.Account;
import java.time.Instant;

/** Confirms an account deletion: the account id, its now-CLOSED status, and when it was closed. */
public record DeletionResponse(String accountId, String status, Instant closedAt) {

  static DeletionResponse from(Account account) {
    return new DeletionResponse(
        account.id().value().toString(), account.status().name(), account.closedAt().orElse(null));
  }
}
