/**
 * The two formatting rules every moderation view shares. Extracted once the queue, a case's
 * concerns and its decisions all needed them — three call sites, not a guess at a fourth.
 */

/**
 * Always UTC, always labelled. These pages are rendered on the server, so a browser-local format
 * would disagree with the server's own clock — and moderation timestamps are read against SLA
 * times, where "which timezone was that?" is not a question anyone should have to ask.
 */
export function formatInstant(timestamp: string | undefined): string {
  if (!timestamp) {
    return "—";
  }
  const moment = new Date(timestamp);
  if (Number.isNaN(moment.getTime())) {
    return "—";
  }
  return `${new Intl.DateTimeFormat("en-GB", {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: "UTC",
  }).format(moment)} UTC`;
}

/** "APPROVE_WITH_REDACTION" as something to read. */
export function humanise(value: string | undefined): string {
  if (!value) {
    return "—";
  }
  return value.charAt(0) + value.slice(1).toLowerCase().replaceAll("_", " ");
}
