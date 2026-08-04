import { describe, expect, it } from "vitest";
import { type AuditEntry, toTimelineRow } from "./timeline";

const ENTRY: AuditEntry = {
  at: "2026-08-04T10:15:00Z",
  actorAccountId: "aa110000-0000-4000-8000-000000000001",
  module: "identity",
  action: "GRANT_ADMIN",
  subjectType: "ACCOUNT",
  subjectId: "bb220000-0000-4000-8000-000000000002",
  outcome: "APPLIED",
  reason: null as unknown as string,
};

describe("a line of the audit timeline", () => {
  it("says who did what to what, and when", () => {
    const row = toTimelineRow(ENTRY);

    expect(row.when).toBe("4 Aug 2026, 10:15 UTC");
    expect(row.actor).toBe("aa110000");
    expect(row.action).toBe("Grant admin");
    expect(row.subject).toBe("Account bb220000");
    expect(row.where).toBe("Identity");
    expect(row.outcome).toBe("Applied");
  });

  it("names the system when nobody decided", () => {
    // Verification expires a lapsed badge on a schedule. "—" would read as missing data; the truth
    // is that no person did this.
    const expiry = toTimelineRow({ ...ENTRY, actorAccountId: undefined, action: "EXPIRE" });

    expect(expiry.actor).toBe("System");
  });

  it("shows a reason where the action required one, and says so where it did not", () => {
    expect(toTimelineRow({ ...ENTRY, reason: "DOXXING" }).reason).toBe("DOXXING");
    // A view of an account requires no reason code; an empty cell would read as one going missing.
    expect(toTimelineRow(ENTRY).reason).toBe("—");
  });

  it("never renders anything a moderator wrote", () => {
    // The API does not send an internal note or a public explanation, and the row must not start
    // showing them if it ever does — the timeline says an action happened, the case says what it
    // contained.
    const leaky = {
      ...ENTRY,
      internalNote: "third from this account",
      publicExplanation: "Your review named a neighbour.",
    } as AuditEntry;

    const rendered = Object.values(toTimelineRow(leaky)).join(" ");

    expect(rendered).not.toContain("third from this account");
    expect(rendered).not.toContain("named a neighbour");
  });

  it("copes with an entry the API described only partially", () => {
    expect(() => toTimelineRow({})).not.toThrow();
    expect(toTimelineRow({}).action).toBe("—");
    expect(toTimelineRow({}).subject).toBe("—");
  });
});
