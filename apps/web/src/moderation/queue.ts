import { formatInstant, humanise } from "@/src/format";
import type { components } from "@/src/api/generated/schema";

export type ModerationCase = components["schemas"]["ModerationCaseResponse"];

/**
 * Everything the queue is allowed to put on screen, and nothing else.
 *
 * <p>The page renders only these fields. That makes "what a moderator can see in the queue" a
 * single testable list rather than a property spread across JSX, and it is where the privacy rule
 * lives: a case is worked from a count of concerns, never from who raised them. The API already
 * withholds reporter identities from {@code ModerationCaseResponse}; this keeps the interface from
 * quietly re-introducing them the first time someone adds a field to the response.
 */
export type QueueRow = {
  caseId: string;
  target: string;
  trigger: string;
  risk: string;
  status: string;
  concerns: string;
  opened: string;
  assignment: string;
};

export function toQueueRow(moderationCase: ModerationCase): QueueRow {
  return {
    caseId: moderationCase.caseId ?? "",
    target: `${humanise(moderationCase.targetType)} ${shorten(moderationCase.targetId)}`,
    trigger: humanise(moderationCase.trigger),
    risk: humanise(moderationCase.riskLevel),
    status: humanise(moderationCase.status),
    concerns: concernSummary(moderationCase.concernCount),
    opened: formatInstant(moderationCase.openedAt),
    assignment: moderationCase.assignedModerator ? "Assigned" : "Unassigned",
  };
}

/**
 * A count, phrased as a count. "1 concern" and "4 concerns" say how much attention a case has
 * attracted without saying whose.
 */
function concernSummary(count: number | undefined): string {
  const concerns = count ?? 0;
  return concerns === 1 ? "1 concern" : `${concerns} concerns`;
}


/** Ids are shown truncated: enough to tell two rows apart, short enough to read. */
function shorten(id: string | undefined): string {
  return id ? id.slice(0, 8) : "—";
}
