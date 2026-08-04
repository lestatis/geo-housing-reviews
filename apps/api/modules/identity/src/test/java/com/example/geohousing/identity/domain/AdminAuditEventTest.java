package com.example.geohousing.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminAuditEventTest {

  private static final Instant AT = Instant.parse("2026-08-04T10:00:00Z");
  private static final AccountId ADMIN = AccountId.of(UUID.randomUUID());
  private static final AccountId TARGET = AccountId.of(UUID.randomUUID());

  @Test
  void anAccountViewNamesWhoLookedAtWhom() {
    AdminAuditEvent event =
        AdminAuditEvent.accountView(UUID.randomUUID(), ADMIN, TARGET, AdminAuditOutcome.FOUND, AT);

    assertThat(event.action()).isEqualTo(AdminAuditAction.VIEW_ACCOUNT);
    assertThat(event.adminAccountId()).isEqualTo(ADMIN);
    assertThat(event.targetAccountId()).contains(TARGET);
    assertThat(event.occurredAt()).isEqualTo(AT);
  }

  @Test
  void aRoleChangeIsRecordedAsTheDirectionItWent() {
    // The log reads as a history of grants and removals rather than a list of states to diff.
    assertThat(
            AdminAuditEvent.roleChange(
                    UUID.randomUUID(),
                    ADMIN,
                    TARGET,
                    AccountRole.ADMIN,
                    AdminAuditOutcome.APPLIED,
                    AT)
                .action())
        .isEqualTo(AdminAuditAction.GRANT_ADMIN);
    assertThat(
            AdminAuditEvent.roleChange(
                    UUID.randomUUID(),
                    ADMIN,
                    TARGET,
                    AccountRole.USER,
                    AdminAuditOutcome.APPLIED,
                    AT)
                .action())
        .isEqualTo(AdminAuditAction.REVOKE_ADMIN);
  }

  @Test
  void aRestrictionRecordsWhicheverActionItWas() {
    assertThat(
            AdminAuditEvent.restriction(
                    UUID.randomUUID(),
                    ADMIN,
                    TARGET,
                    AdminAuditAction.LIFT_RESTRICTION,
                    AdminAuditOutcome.APPLIED,
                    AT)
                .action())
        .isEqualTo(AdminAuditAction.LIFT_RESTRICTION);
  }

  @Test
  void readingTheAuditNamesNoTarget() {
    // The subject is the log itself. Naming one account would misdescribe a query that may have
    // spanned everybody — and would put an account id in a row nobody looked that account up in.
    AdminAuditEvent event =
        AdminAuditEvent.auditView(UUID.randomUUID(), ADMIN, AdminAuditOutcome.FOUND, AT);

    assertThat(event.action()).isEqualTo(AdminAuditAction.VIEW_AUDIT);
    assertThat(event.adminAccountId()).isEqualTo(ADMIN);
    assertThat(event.targetAccountId()).isEmpty();
    assertThat(event.occurredAt()).isEqualTo(AT);
  }

  @Test
  void everyEventNamesTheAdminAndWhenItHappened() {
    assertThatThrownBy(
            () -> AdminAuditEvent.auditView(UUID.randomUUID(), null, AdminAuditOutcome.FOUND, AT))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                AdminAuditEvent.auditView(UUID.randomUUID(), ADMIN, AdminAuditOutcome.FOUND, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void anEventAboutSomebodyAlwaysNamesThem() {
    // accountView and restriction both target an account; a row that lost it would record that
    // something happened to nobody.
    assertThatThrownBy(
            () ->
                AdminAuditEvent.accountView(
                    UUID.randomUUID(), ADMIN, null, AdminAuditOutcome.FOUND, AT))
        .isInstanceOf(NullPointerException.class);
  }
}
