import { formatInstant, humanise } from "@/src/format";
import type { components } from "@/src/api/generated/schema";

export type Concern = components["schemas"]["ConcernResponse"];
export type Decision = components["schemas"]["ModerationDecisionResponse"];
export type CaseDetail = components["schemas"]["ModerationCaseDetailResponse"];

/**
 * Everything the case screen is allowed to show, and nothing else — the same allowlist idea as the
 * queue, applied where there is more to leak.
 *
 * <p>A concern is what somebody said was wrong. Who said it is not part of judging the content, and
 * the API withholds it for that reason; this keeps the screen from re-introducing it the first time
 * someone adds a field to `ConcernResponse`.
 */
export type ConcernRow = {
  category: string;
  description: string;
  raised: string;
};

export function toConcernRow(concern: Concern): ConcernRow {
  return {
    category: humanise(concern.category),
    // A category with nothing written is legitimate for everything except "Other"; saying so beats
    // an empty cell that reads as a rendering fault.
    description: concern.description?.trim() || "No further detail given.",
    raised: formatInstant(concern.raisedAt),
  };
}

/**
 * A decision as the case history shows it.
 *
 * <p>`publicExplanation` and `internalNote` stay separate fields and are never concatenated. The
 * note is written by one moderator for another; the explanation is what the author was told. An
 * author-facing view is built from the explanation alone, and merging them here is exactly how the
 * note would one day follow it out of the building.
 */
export type DecisionRow = {
  action: string;
  reasonCode: string;
  publicExplanation: string;
  internalNote: string;
  decidedBy: string;
  decided: string;
};

export function toDecisionRow(decision: Decision): DecisionRow {
  return {
    action: humanise(decision.action),
    reasonCode: decision.reasonCode ?? "—",
    publicExplanation: decision.publicExplanation?.trim() || "—",
    internalNote: decision.internalNote?.trim() || "—",
    decidedBy: decision.decidedBy ? decision.decidedBy.slice(0, 8) : "—",
    decided: formatInstant(decision.decidedAt),
  };
}
