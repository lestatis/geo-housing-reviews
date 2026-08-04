package com.example.geohousing.app.audit;

import com.example.geohousing.shared.audit.AuditCursor;
import com.example.geohousing.shared.audit.AuditEntry;
import java.util.List;
import java.util.Objects;

/**
 * One page of the timeline, and where the next one starts.
 *
 * @param entries newest first, at most the query's limit
 * @param nextCursor {@code null} when nothing remains behind this page — distinct from a cursor
 *     onto an empty page, which would leave a reader unable to tell "that is all" from "the query
 *     stopped answering"
 */
public record AuditPage(List<AuditEntry> entries, AuditCursor nextCursor) {

  public AuditPage {
    entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
  }
}
