package com.example.geohousing.verification.application;

import com.example.geohousing.verification.domain.EvidenceAccessEvent;
import java.util.ArrayList;
import java.util.List;

/** Captures the evidence access audit so tests can assert reads and deletions were recorded. */
final class RecordingEvidenceAccessAudit implements EvidenceAccessAuditRepository {

  final List<EvidenceAccessEvent> events = new ArrayList<>();

  @Override
  public void record(EvidenceAccessEvent event) {
    events.add(event);
  }

  EvidenceAccessEvent last() {
    return events.get(events.size() - 1);
  }
}
