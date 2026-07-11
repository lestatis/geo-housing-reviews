package com.example.geohousing.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountTest {

  private static final Clock FIXED =
      Clock.fixed(Instant.parse("2026-07-11T10:00:00Z"), ZoneOffset.UTC);
  private static final AccountId ID = AccountId.of(UUID.randomUUID());

  @Test
  void provisionCreatesActiveUserAccount() {
    Account account = Account.provision(ID, "auth|abc", "user@example.com", FIXED);

    assertThat(account.id()).isEqualTo(ID);
    assertThat(account.authSubjectHash()).isEqualTo("auth|abc");
    assertThat(account.email()).contains("user@example.com");
    assertThat(account.role()).isEqualTo(AccountRole.USER);
    assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
    assertThat(account.isClosed()).isFalse();
    assertThat(account.createdAt()).isEqualTo(FIXED.instant());
    assertThat(account.closedAt()).isEmpty();
    assertThat(account.version()).isZero();
  }

  @Test
  void closeMarksAccountClosedScrubsEmailAndStampsClosedAt() {
    Account account = Account.provision(ID, "auth|abc", "user@example.com", FIXED);

    account.close(FIXED);

    assertThat(account.isClosed()).isTrue();
    assertThat(account.status()).isEqualTo(AccountStatus.CLOSED);
    assertThat(account.closedAt()).contains(FIXED.instant());
    assertThat(account.email()).isEmpty();
  }

  @Test
  void closeIsIdempotent() {
    Account account = Account.provision(ID, "auth|abc", "user@example.com", FIXED);
    Clock later = Clock.fixed(Instant.parse("2026-08-01T00:00:00Z"), ZoneOffset.UTC);

    account.close(FIXED);
    account.close(later);

    assertThat(account.closedAt()).contains(FIXED.instant());
  }

  @Test
  void provisionAllowsNullEmail() {
    Account account = Account.provision(ID, "auth|abc", null, FIXED);

    assertThat(account.email()).isEmpty();
  }
}
