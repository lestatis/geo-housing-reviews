import { formatInstant, humanise } from "@/src/format";
import type { components } from "@/src/api/generated/schema";

export type PendingAppeal = components["schemas"]["AdminAppealQueueEntryResponse"];

/**
 * An appeal as the moderator hearing it reads it: both sides, and a way to the content.
 *
 * <p>An appeal is heard by someone other than the moderator who decided (MODERATION.md), so
 * whoever opens this queue has no memory of the case. That is why the row carries the contested
 * decision at all, and why it names the original decider — being refused by the API is a poor way
 * to learn the appeal is against your own decision.
 *
 * <p>The decision's `internalNote` reaches this app but is deliberately left off this row. It is
 * one moderator's characterisation of the author ("third from this account"), and putting it at the
 * top of a fresh hearing is how an appeal becomes a rubber stamp on the first decision. It is still
 * one click away on the case, where it is read as history rather than as the case for the
 * prosecution.
 */
export type AppealRow = {
  appealId: string;
  appealText: string;
  contested: string;
  toldTheAuthor: string;
  originalDecider: string;
  caseId: string;
  target: string;
  filed: string;
};

export function toAppealRow(appeal: PendingAppeal): AppealRow {
  const decision = appeal.contestedDecision ?? {};
  return {
    appealId: appeal.appealId ?? "",
    appealText: appeal.appealText?.trim() || "—",
    contested: decision.action
      ? `${humanise(decision.action)} · ${decision.reasonCode ?? "—"}`
      : "—",
    toldTheAuthor: decision.publicExplanation?.trim() || "—",
    originalDecider: shorten(appeal.originalDecider),
    caseId: decision.caseId ?? "",
    target: `${humanise(decision.targetType)} ${shorten(decision.targetId)}`,
    filed: formatInstant(appeal.createdAt),
  };
}

/** Ids are shown truncated: enough to tell two moderators apart, short enough to read. */
function shorten(id: string | undefined): string {
  return id ? id.slice(0, 8) : "—";
}
