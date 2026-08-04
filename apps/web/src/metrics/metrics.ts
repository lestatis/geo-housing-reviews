import type { components } from "@/src/api/generated/schema";

export type AdminMetrics = components["schemas"]["AdminMetrics"];

export type MetricRow = { label: string; value: string };

/**
 * The queue block, as labels a reader can act on.
 *
 * <p>Same allowlist idea as the other screens: what the page shows is written here, not inferred
 * from whatever the response happens to contain. Metrics carry no personal data today, and this is
 * what keeps that true if the response ever grows a field.
 */
export function toMetricRows(metrics: AdminMetrics): MetricRow[] {
  return [
    { label: "Open moderation cases", value: count(metrics.openModerationCases) },
    { label: "Reviews awaiting moderation", value: count(metrics.reviewsAwaitingModeration) },
    { label: "Verifications pending", value: count(metrics.pendingVerifications) },
    { label: "Oldest open case", value: age(metrics.oldestOpenCaseAgeDays) },
  ];
}

/**
 * The one measurement `docs/MODERATION.md` asks for by name.
 *
 * <p>Counts first, rate in brackets. A bare "33%" over three appeals reads as a finding about the
 * platform; "1 of 3" is the same fact at its real size. And an empty window says no appeals were
 * *heard* rather than that none were overturned — the second is a claim about outcomes that nobody
 * has made.
 */
export function toOverturnLine(metrics: AdminMetrics): string {
  const heard = metrics.appealsHeard ?? 0;
  if (heard === 0) {
    return "No appeals heard in this window";
  }
  const overturned = metrics.appealsOverturned ?? 0;
  const percentage = metrics.appealOverturnPercentage ?? Math.round((100 * overturned) / heard);
  return `${overturned} of ${heard} overturned (${percentage}%)`;
}

/** Throughput, which is only meaningful in pairs — see the API's own note on why. */
export function toThroughputRows(metrics: AdminMetrics): MetricRow[] {
  return [
    { label: "Moderation decisions", value: count(metrics.moderationDecisions) },
    {
      label: "Reviews published / removed",
      value: `${count(metrics.reviewsPublished)} / ${count(metrics.reviewsRemoved)}`,
    },
    {
      label: "Verifications approved / rejected",
      value: `${count(metrics.verificationsApproved)} / ${count(metrics.verificationsRejected)}`,
    },
  ];
}

function count(value: number | undefined): string {
  return String(value ?? 0);
}

/** Days, in words. "0 days" would read as a rounding artefact rather than as "it arrived today". */
function age(days: number | null | undefined): string {
  if (days === null || days === undefined) {
    return "—";
  }
  if (days === 0) {
    return "today";
  }
  return days === 1 ? "1 day" : `${days} days`;
}
