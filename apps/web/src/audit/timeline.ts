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

/**
 * A window as the screen states it: two **inclusive** UTC calendar dates.
 *
 * <p>Inclusive at both ends is what the date inputs mean to the person typing them — "to 4 August"
 * is a request for the 4th, not for everything up to the moment it began. Turning that into the
 * half-open range the API takes is {@link windowBounds}'s job, and doing it in one place is the
 * point: the screen previously sent `23:59:59Z`, which silently dropped anything recorded in the
 * last second of the chosen day.
 */
export type TimelineWindow = { since: string; until: string };

/** Today plus the six days before it — seven inclusive days, which is what the screen says. */
const DEFAULT_DAYS = 7;

const DATE = /^\d{4}-\d{2}-\d{2}$/;

/**
 * Which window the timeline is showing, always stated.
 *
 * <p>A screen that quietly applied a default would let somebody conclude nothing happened when they
 * were looking at the wrong week — which, for an audit log, is the failure that matters.
 *
 * <p>Anything that is not a calendar date falls back to the default rather than being passed on: a
 * rejected query tells the reader nothing, and a silently mangled one tells them something false.
 */
export function describeWindow(
  since: string | undefined,
  until: string | undefined,
  now: Date,
): TimelineWindow {
  const end = asCalendarDate(until) ?? asDate(now);
  const start =
    asCalendarDate(since) ??
    asDate(new Date(now.getTime() - (DEFAULT_DAYS - 1) * 24 * 60 * 60 * 1000));
  return { since: start, until: end };
}

/**
 * The window as the API takes it: {@code since} inclusive, {@code until} exclusive.
 *
 * <p>The end date becomes the start of the *next* day. Every adapter's SQL asks for
 * {@code created_at < :until}, so sending the end of the chosen day — by any number of nines —
 * drops whatever was recorded in the sliver after it. Midnight of the following day has no sliver.
 */
export function windowBounds(window: TimelineWindow): { since: string; until: string } {
  return { since: `${window.since}T00:00:00Z`, until: nextMidnight(window.until) };
}

function nextMidnight(date: string): string {
  const next = new Date(`${date}T00:00:00Z`);
  next.setUTCDate(next.getUTCDate() + 1);
  const iso = next.toISOString();
  // Beyond year 9999 an ISO instant grows a sign and a fifth digit, which the API will not parse.
  // Nothing can be recorded after the end of representable time, so clamping there loses nothing.
  return iso.startsWith("+") ? "9999-12-31T23:59:59.999Z" : iso;
}

function asCalendarDate(value: string | undefined): string | undefined {
  const trimmed = value?.trim();
  if (!trimmed || !DATE.test(trimmed)) {
    return undefined;
  }
  // The round trip is the check. JavaScript rejects a thirteenth month but *normalizes* a day that
  // overruns its month — `2026-02-31` becomes 3 March — so a date that parses is not necessarily
  // the date that was written. Accepting it would show one window and ask the API for another.
  const parsed = new Date(`${trimmed}T00:00:00Z`);
  if (Number.isNaN(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== trimmed) {
    return undefined;
  }
  return trimmed;
}

function asDate(moment: Date): string {
  return moment.toISOString().slice(0, 10);
}
