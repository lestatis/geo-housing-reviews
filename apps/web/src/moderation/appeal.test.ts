import { describe, expect, it } from "vitest";
import { type PendingAppeal, toAppealRow } from "./appeal";

const APPEAL: PendingAppeal = {
  appealId: "c4d5e600-0000-4000-8000-000000000001",
  appealText: "I never named anyone. The flat number was my own.",
  status: "PENDING",
  originalDecider: "b3c1d200-0000-4000-8000-000000000009",
  createdAt: "2026-07-30T12:00:00Z",
  contestedDecision: {
    caseId: "8a1f2c3d-0000-4000-8000-000000000002",
    targetType: "REVIEW",
    targetId: "3f2a9b41-0000-4000-8000-000000000003",
    action: "REMOVE",
    reasonCode: "DOXXING",
    publicExplanation: "Your review named a neighbour.",
    internalNote: "Third from this account.",
    decidedAt: "2026-07-30T11:00:00Z",
  },
};

describe("an appeal waiting to be heard", () => {
  it("shows both sides: what was decided and what the appellant says about it", () => {
    // A moderator hearing an appeal is by rule not the one who decided, so they arrive with no
    // memory of the case. Either half alone is a request to guess.
    const row = toAppealRow(APPEAL);

    expect(row.appealText).toBe("I never named anyone. The flat number was my own.");
    expect(row.contested).toBe("Remove · DOXXING");
    expect(row.toldTheAuthor).toBe("Your review named a neighbour.");
    expect(row.filed).toBe("30 Jul 2026, 12:00 UTC");
  });

  it("points at the case so the content can be read in full", () => {
    expect(toAppealRow(APPEAL).caseId).toBe("8a1f2c3d-0000-4000-8000-000000000002");
    expect(toAppealRow(APPEAL).target).toBe("Review 3f2a9b41");
  });

  it("names the moderator being appealed against", () => {
    // Whoever picks this up needs to know it is not them — the API refuses it either way, but
    // finding out by being refused is a worse way to learn.
    expect(toAppealRow(APPEAL).originalDecider).toBe("b3c1d200");
  });

  it("copes with an appeal the API described only partially", () => {
    expect(() => toAppealRow({})).not.toThrow();
    expect(toAppealRow({}).contested).toBe("—");
    expect(toAppealRow({}).caseId).toBe("");
  });
});
