package com.example.geohousing.identity.api;

import java.util.UUID;

/**
 * Records that somebody read the audit timeline.
 *
 * <p>Identity owns the admin audit table, so the merged timeline — which lives in the app, the only
 * place entitled to hold every module's trail — asks identity to record its own access rather than
 * writing to another module's table.
 *
 * <p>Narrow on purpose: one method, one direction, and nothing that could be used to record
 * anything else. The vocabulary of what may be audited stays identity's.
 */
public interface AuditReadRecorder {

  void recordTimelineRead(UUID adminAccountId);
}
