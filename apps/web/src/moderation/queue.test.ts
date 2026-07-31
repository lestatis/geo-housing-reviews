import { describe, expect, it } from "vitest";
import { type ModerationCase, toQueueRow } from "./queue";

const REPORTED_CASE: ModerationCase = {
  caseId: "8a1f2c3d-0000-4000-8000-000000000001",
  targetType: "REVIEW",
  targetId: "3f2a9b41-0000-4000-8000-000000000002",
  trigger: "REPORT",
  riskLevel: "STANDARD",
  status: "OPEN",
  concernCount: 3,
  openedAt: "2026-07-30T09:15:00Z",
  assignedModerator: undefined,
};

describe("the moderation queue row", () => {
  it("says how much concern a case attracted", () => {
    expect(toQueueRow({ ...REPORTED_CASE, concernCount: 3 }).concerns).toBe("3 concerns");
    expect(toQueueRow({ ...REPORTED_CASE, concernCount: 1 }).concerns).toBe("1 concern");
    expect(toQueueRow({ ...REPORTED_CASE, concernCount: undefined }).concerns).toBe("0 concerns");
  });

  it("never carries a reporter's identity", () => {
    // The whole reason ModerationCaseResponse counts rather than lists. If the API ever grows a
    // reporter field, the row must not pick it up — a moderator decides on content, and knowing
    // who complained is how a queue turns into a way to retaliate.
    const withLeakedReporters = {
      ...REPORTED_CASE,
      reporters: ["ea55...-nino", "bb77...-giorgi"],
      reporterAccountIds: ["ea550000-0000-4000-8000-000000000003"],
    } as ModerationCase;

    const rendered = Object.values(toQueueRow(withLeakedReporters)).join(" ");

    expect(rendered).not.toContain("nino");
    expect(rendered).not.toContain("giorgi");
    expect(rendered).not.toContain("ea550000");
  });

  it("shows what is being moderated without spelling out a whole identifier", () => {
    const row = toQueueRow(REPORTED_CASE);

    expect(row.target).toBe("Review 3f2a9b41");
    expect(row.trigger).toBe("Report");
    expect(toQueueRow({ ...REPORTED_CASE, trigger: "PRE_MODERATION" }).trigger).toBe(
      "Pre moderation",
    );
    expect(row.status).toBe("Open");
  });

  it("says when nobody has picked a case up", () => {
    expect(toQueueRow(REPORTED_CASE).assignment).toBe("Unassigned");
    expect(toQueueRow({ ...REPORTED_CASE, assignedModerator: "someone" }).assignment).toBe(
      "Assigned",
    );
  });

  it("times a case in UTC, said out loud", () => {
    // The server renders this, so a browser-local format would disagree with the clock the queue
    // is measured against.
    expect(toQueueRow(REPORTED_CASE).opened).toBe("30 Jul 2026, 09:15 UTC");
    expect(toQueueRow({ ...REPORTED_CASE, openedAt: "not a date" }).opened).toBe("—");
  });

  it("copes with a case the API described only partially", () => {
    expect(() => toQueueRow({})).not.toThrow();
    expect(toQueueRow({}).target).toBe("— —");
  });
});
