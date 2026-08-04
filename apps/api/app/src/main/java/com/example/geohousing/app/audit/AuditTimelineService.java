package com.example.geohousing.app.audit;

import com.example.geohousing.identity.api.AuditReadRecorder;
import com.example.geohousing.shared.audit.AuditCursor;
import com.example.geohousing.shared.audit.AuditEntry;
import com.example.geohousing.shared.audit.AuditTrail;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One timeline out of every module's trail.
 *
 * <p>The app is the only place that may hold all five at once: a module knows what it did and
 * nothing about what the others did, which is the boundary working rather than a limitation to
 * route around. Spring collects every {@link AuditTrail} bean, so a module that starts recording
 * something appears here without this class learning its name.
 *
 * <p>Each trail is asked for the full limit and the merged result is trimmed again. Trimming per
 * trail first would return the newest few from each module rather than the newest few overall — a
 * timeline that silently omits the most recent thing that happened.
 */
public class AuditTimelineService {

  private final List<AuditTrail> trails;
  private final AuditReadRecorder auditReadRecorder;

  public AuditTimelineService(List<AuditTrail> trails, AuditReadRecorder auditReadRecorder) {
    this.trails = List.copyOf(Objects.requireNonNull(trails, "trails"));
    this.auditReadRecorder = Objects.requireNonNull(auditReadRecorder, "auditReadRecorder");
  }

  /**
   * The timeline, and a record that it was read.
   *
   * <p>Recorded before the answer is returned, and a failure to record propagates: reading who did
   * what to whom is exactly the access an audit log exists to capture, so an unrecorded read must
   * not be a successful one.
   */
  public AuditPage recorded(AuditQuery query, UUID readBy) {
    Objects.requireNonNull(query, "query");
    Objects.requireNonNull(readBy, "readBy");
    AuditPage page = merged(query);
    auditReadRecorder.recordTimelineRead(readBy);
    return page;
  }

  /**
   * Asks every trail for one more entry than the page holds.
   *
   * <p>That extra entry is the whole test for "is there more": with exactly {@code limit} asked
   * for, a merge of exactly {@code limit} entries could equally mean the history ended there or
   * that one trail was cut off mid-answer, and the two need different answers to the reader.
   */
  private AuditPage merged(AuditQuery query) {
    AuditCursor after = query.startsAt();
    List<AuditEntry> found =
        trails.stream()
            .flatMap(
                trail ->
                    trail
                        .recorded(
                            query.from(), after, query.actor().orElse(null), query.limit() + 1)
                        .stream())
            .sorted(AuditEntry.NEWEST_FIRST)
            .limit(query.limit() + 1L)
            .toList();

    if (found.size() <= query.limit()) {
      return new AuditPage(found, null);
    }
    List<AuditEntry> page = found.subList(0, query.limit());
    AuditEntry last = page.get(page.size() - 1);
    return new AuditPage(page, new AuditCursor(last.at(), last.module(), last.id()));
  }
}
