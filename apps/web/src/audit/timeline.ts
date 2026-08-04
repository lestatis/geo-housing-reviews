import { formatInstant, humanise } from "@/src/format";
import type { components } from "@/src/api/generated/schema";

export type AuditEntry = components["schemas"]["AuditEntryView"];

/**
 * Everything the timeline may show, and nothing else.
 *
 * <p>The same allowlist idea as the other screens, guarding something the API already withholds: an
 * audit entry carries no internal note and no public explanation. The timeline says an action
 * happened; the case or account screen says what it contained. This is what stops that changing
 * quietly if the response ever grows a field.
 */
export type TimelineRow = {
  when: string;
  actor: string;
  where: string;
  action: string;
  subject: string;
  outcome: string;
  reason: string;
};

export function toTimelineRow(entry: AuditEntry): TimelineRow {
  return {
    when: formatInstant(entry.at),
    // "System" rather than a dash: verification expires a lapsed badge on a schedule, and no person
    // decided it. A dash would read as data that went missing.
    actor: entry.actorAccountId ? entry.actorAccountId.slice(0, 8) : "System",
    where: humanise(entry.module),
    action: humanise(entry.action),
    subject: entry.subjectId
      ? `${humanise(entry.subjectType)} ${entry.subjectId.slice(0, 8)}`
      : humanise(entry.subjectType),
    outcome: humanise(entry.outcome),
    reason: entry.reason?.trim() || "—",
  };
}
