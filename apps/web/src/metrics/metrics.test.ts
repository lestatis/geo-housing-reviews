import { describe, expect, it } from "vitest";
import { type AdminMetrics, toMetricRows, toOverturnLine } from "./metrics";

const METRICS: AdminMetrics = {
  openModerationCases: 12,
  reviewsAwaitingModeration: 31,
  pendingVerifications: 4,
  oldestOpenCaseAgeDays: 3,
  moderationDecisions: 87,
  appealsHeard: 14,
  appealsOverturned: 3,
  appealOverturnPercentage: 21,
  verificationsApproved: 22,
  verificationsRejected: 7,
  reviewsPublished: 64,
  reviewsRemoved: 9,
};

describe("the overturn line, which is the number the documents ask for", () => {
  it("leads with the counts and puts the rate second", () => {
    // "21%" alone invites reading a rate off fourteen appeals as a finding about the platform.
    // The counts are what make it interpretable, so they come first.
    expect(toOverturnLine(METRICS)).toBe("3 of 14 overturned (21%)");
  });

  it("says nothing was heard rather than claiming nothing was overturned", () => {
    // 0% is a claim about outcomes. No appeals is a statement about the queue, and the only true
    // one when nobody has decided anything.
    const quiet = { ...METRICS, appealsHeard: 0, appealsOverturned: 0, appealOverturnPercentage: null };

    expect(toOverturnLine(quiet)).toBe("No appeals heard in this window");
  });

  it("reads honestly when a single appeal was overturned", () => {
    const tiny = { ...METRICS, appealsHeard: 1, appealsOverturned: 1, appealOverturnPercentage: 100 };

    // "100%" on its own would be alarming; "1 of 1" is the same fact, correctly sized.
    expect(toOverturnLine(tiny)).toBe("1 of 1 overturned (100%)");
  });
});

describe("the queue block", () => {
  it("states each number with a label an administrator can act on", () => {
    const rows = toMetricRows(METRICS);

    expect(rows).toContainEqual({ label: "Open moderation cases", value: "12" });
    expect(rows).toContainEqual({ label: "Reviews awaiting moderation", value: "31" });
    expect(rows).toContainEqual({ label: "Verifications pending", value: "4" });
  });

  it("gives the oldest case as an age, in the reader's terms", () => {
    expect(toMetricRows(METRICS)).toContainEqual({
      label: "Oldest open case",
      value: "3 days",
    });
    expect(toMetricRows({ ...METRICS, oldestOpenCaseAgeDays: 1 })).toContainEqual({
      label: "Oldest open case",
      value: "1 day",
    });
    expect(toMetricRows({ ...METRICS, oldestOpenCaseAgeDays: 0 })).toContainEqual({
      label: "Oldest open case",
      value: "today",
    });
  });

  it("says the queue is empty rather than showing an age of nothing", () => {
    const empty = { ...METRICS, openModerationCases: 0, oldestOpenCaseAgeDays: null };

    expect(toMetricRows(empty)).toContainEqual({ label: "Oldest open case", value: "—" });
  });

  it("copes with a response the API described only partially", () => {
    expect(() => toMetricRows({})).not.toThrow();
    expect(toMetricRows({})).toContainEqual({ label: "Open moderation cases", value: "0" });
    expect(toOverturnLine({})).toBe("No appeals heard in this window");
  });
});
