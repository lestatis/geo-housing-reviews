import { describe, expect, it } from "vitest";
import { type Concern, type Decision, toConcernRow, toDecisionRow } from "./case";

const CONCERN: Concern = {
  category: "PERSONAL_DATA",
  description: "It names my neighbour's flat.",
  raisedAt: "2026-07-30T09:15:00Z",
};

const DECISION: Decision = {
  action: "REMOVE",
  reasonCode: "DOXXING",
  publicExplanation: "Your review named a neighbour.",
  internalNote: "Third one from this account this week.",
  decidedBy: "b3c1d200-0000-4000-8000-000000000009",
  decidedAt: "2026-07-30T11:00:00Z",
  policyVersion: 1,
  affectedTargetVersion: 2,
};

describe("a concern on a case", () => {
  it("carries what was said, not who said it", () => {
    // The substance is the concern; the reporter is not part of judging the content, and a
    // moderator who knows which neighbour complained is a moderator who can be leaned on.
    const leaky = {
      ...CONCERN,
      reporter: "giorgi-neighbour",
      reporterAccountId: "ea550000-0000-4000-8000-000000000003",
    } as Concern;

    const rendered = Object.values(toConcernRow(leaky)).join(" ");

    expect(rendered).toContain("It names my neighbour's flat.");
    expect(rendered).not.toContain("giorgi");
    expect(rendered).not.toContain("ea550000");
  });

  it("says a category in words, and admits when nothing was written", () => {
    expect(toConcernRow(CONCERN).category).toBe("Personal data");
    expect(toConcernRow({ ...CONCERN, description: undefined }).description).toBe(
      "No further detail given.",
    );
  });

  it("times the concern in UTC", () => {
    expect(toConcernRow(CONCERN).raised).toBe("30 Jul 2026, 09:15 UTC");
  });
});

describe("a decision already recorded on a case", () => {
  it("shows what was done, why, and what the author was told", () => {
    const row = toDecisionRow(DECISION);

    expect(row.action).toBe("Remove");
    expect(row.reasonCode).toBe("DOXXING");
    expect(row.publicExplanation).toBe("Your review named a neighbour.");
    expect(row.decided).toBe("30 Jul 2026, 11:00 UTC");
  });

  it("keeps the internal note separate from what the author was told", () => {
    // internalNote is a note between moderators. It is admin-only by construction, and the two
    // must never be rendered as one field — an author-facing view is built from publicExplanation
    // alone, and merging them here is how the note would eventually follow it out.
    const row = toDecisionRow(DECISION);

    expect(row.internalNote).toBe("Third one from this account this week.");
    expect(row.publicExplanation).not.toContain("Third one");
  });

  it("says plainly when an action carried no explanation", () => {
    const approved = toDecisionRow({ ...DECISION, action: "APPROVE", publicExplanation: undefined });

    expect(approved.publicExplanation).toBe("—");
  });

  it("copes with a decision the API described only partially", () => {
    expect(() => toDecisionRow({})).not.toThrow();
    expect(toDecisionRow({}).action).toBe("—");
  });
});
