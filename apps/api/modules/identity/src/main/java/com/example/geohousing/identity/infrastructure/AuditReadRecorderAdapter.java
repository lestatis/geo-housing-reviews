package com.example.geohousing.identity.infrastructure;

import com.example.geohousing.identity.api.AuditReadRecorder;
import com.example.geohousing.identity.application.AdminAuditEventRepository;
import com.example.geohousing.identity.domain.AccountId;
import com.example.geohousing.identity.domain.AdminAuditEvent;
import com.example.geohousing.identity.domain.AdminAuditOutcome;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Writes the timeline-read event through identity's own append-only audit repository. */
@Component
class AuditReadRecorderAdapter implements AuditReadRecorder {

  private final AdminAuditEventRepository events;
  private final Clock clock;

  AuditReadRecorderAdapter(AdminAuditEventRepository events, Clock identityClock) {
    this.events = Objects.requireNonNull(events, "events");
    this.clock = Objects.requireNonNull(identityClock, "identityClock");
  }

  @Override
  public void recordTimelineRead(UUID adminAccountId) {
    events.record(
        AdminAuditEvent.auditView(
            UUID.randomUUID(),
            AccountId.of(adminAccountId),
            // A read that got as far as being recorded found the log; there is no other outcome
            // this action can have.
            AdminAuditOutcome.FOUND,
            clock.instant()));
  }
}
