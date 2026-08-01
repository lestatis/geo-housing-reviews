import { describe, expect, it } from "vitest";
import { DECISION_ACTIONS, describeAction, requiresPublicExplanation } from "./decision";

describe("what a decision owes the person it affects", () => {
  it("demands an explanation from anything that takes something away", () => {
    // MODERATION.md: anything costing someone their content or access owes them a reason specific
    // enough to act on. Mirrors DecisionAction.requiresPublicExplanation on the server, which is
    // the authority — this only stops the form submitting something the API will refuse.
    expect(requiresPublicExplanation("REJECT")).toBe(true);
    expect(requiresPublicExplanation("HIDE")).toBe(true);
    expect(requiresPublicExplanation("REMOVE")).toBe(true);
    expect(requiresPublicExplanation("REQUEST_CHANGES")).toBe(true);
    expect(requiresPublicExplanation("APPROVE_WITH_REDACTION")).toBe(true);
    expect(requiresPublicExplanation("RESTRICT_ACCOUNT")).toBe(true);
  });

  it("does not demand one where there is no outcome to explain", () => {
    // An approval takes nothing away, and an escalation has not decided anything yet.
    expect(requiresPublicExplanation("APPROVE")).toBe(false);
    expect(requiresPublicExplanation("ESCALATE")).toBe(false);
  });

  it("treats an unknown action as owing an explanation", () => {
    // If this list ever falls behind the server's, the safe direction to be wrong in is asking for
    // too much explanation rather than letting a silent takedown through.
    expect(requiresPublicExplanation("SOMETHING_NEW")).toBe(true);
  });

  it("offers every action the server knows about", () => {
    expect(DECISION_ACTIONS).toEqual([
      "APPROVE",
      "APPROVE_WITH_REDACTION",
      "REQUEST_CHANGES",
      "REJECT",
      "HIDE",
      "REMOVE",
      "RESTRICT_ACCOUNT",
      "ESCALATE",
    ]);
  });

  it("says each action in words a moderator reads rather than a constant", () => {
    expect(describeAction("APPROVE_WITH_REDACTION")).toBe("Approve with redaction");
    expect(describeAction("REMOVE")).toBe("Remove");
    expect(describeAction("SOMETHING_NEW")).toBe("Something new");
  });
});
