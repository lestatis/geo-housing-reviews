/**
 * A window as a screen states it: two **inclusive** UTC calendar dates.
 *
 * <p>Inclusive at both ends is what the date inputs mean to the person typing them — "to 4 August"
 * is a request for the 4th, not for everything up to the moment it began. Turning that into the
 * half-open range the API takes is {@link windowBounds}'s job, and doing it in one place is the
 * point. The audit screen once sent `23:59:59Z` against SQL asking for `< :until` and silently
 * dropped anything recorded in the last sliver of the chosen day; two screens now share this rule
 * precisely so there is only one place for it to be wrong.
 *
 * <p>`defaultDays` is the caller's, because "the last seven days" and "the last thirty" are
 * different questions asked by different screens — the counting rule is shared, the span is not.
 */
export type DateWindow = { since: string; until: string };

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
  defaultDays: number,
): DateWindow {
  const end = asCalendarDate(until) ?? asDate(now);
  const start =
    asCalendarDate(since) ??
    asDate(new Date(now.getTime() - (defaultDays - 1) * 24 * 60 * 60 * 1000));
  return { since: start, until: end };
}

/**
 * The window as the API takes it: {@code since} inclusive, {@code until} exclusive.
 *
 * <p>The end date becomes the start of the *next* day. Every adapter's SQL asks for
 * {@code created_at < :until}, so sending the end of the chosen day — by any number of nines —
 * drops whatever was recorded in the sliver after it. Midnight of the following day has no sliver.
 */
export function windowBounds(window: DateWindow): { since: string; until: string } {
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
