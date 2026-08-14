package com.example.geohousing.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.AccountRole;
import com.example.geohousing.identity.domain.AdminAuditEvent;
import com.example.geohousing.identity.domain.AdminAuditOutcome;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The port's own promise about refusals.
 *
 * <p>{@link AdminAuditEventRepository#recordRefusedAttempt} exists so a refusal survives the
 * rollback its exception causes. Only the JPA adapter needs a transaction of its own to manage
 * that; every other store — every in-memory fake, anything with nothing to escape — should simply
 * write it. The default says so, and a default nobody tests is a promise nobody keeps.
 */
class AdminAuditEventRepositoryTest {

  @Test
  void aStoreWithNoTransactionToEscapeRecordsARefusalTheOrdinaryWay() {
    List<AdminAuditEvent> written = new ArrayList<>();
    AdminAuditEventRepository ordinary = written::add;

    ordinary.recordRefusedAttempt(refusal());

    assertThat(written)
        .as("the refusal was dropped, so the attempt left no record at all")
        .hasSize(1);
    assertThat(written.getFirst().outcome()).isEqualTo(AdminAuditOutcome.REFUSED);
  }

  private static AdminAuditEvent refusal() {
    return AdminAuditEvent.roleChange(
        UUID.randomUUID(),
        AccountId.of(UUID.randomUUID()),
        AccountId.of(UUID.randomUUID()),
        AccountRole.USER,
        AdminAuditOutcome.REFUSED,
        Instant.parse("2026-08-01T10:00:00Z"));
  }
}
