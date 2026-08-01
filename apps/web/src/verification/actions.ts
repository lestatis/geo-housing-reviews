"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { serverApi } from "@/src/api/client";
import { expiryInstantFor } from "./case";

/**
 * Verification decisions, taken on the server like every other write in this app.
 *
 * <p>Each carries the version the moderator was looking at. Two moderators opening the same case is
 * ordinary; without the version the second one silently overwrites the first, and a verification
 * decision is not something to lose quietly.
 */

export type ActionResult = { error: string } | undefined;

type Outcome = "approve" | "reject" | "revoke";

/**
 * Bound to its case id by the caller, so the form's action is a server action rather than a
 * client-side closure around one — a form wrapped in a closure cannot be submitted until the page
 * has hydrated, and a click that lands first is swallowed without a word.
 */
export async function decideVerification(
  caseId: string,
  _previous: ActionResult,
  form: FormData,
): Promise<ActionResult> {
  const outcome = outcomeOf(form);
  const reasonCode = String(form.get("reasonCode") ?? "").trim();
  const validThrough = String(form.get("validThrough") ?? "").trim();
  const version = Number(form.get("version") ?? Number.NaN);

  if (!reasonCode) {
    // Every verification decision is a decision about a person's standing; an unexplained one
    // leaves nothing for a later reviewer, or an appeal, to work from.
    return { error: "Every verification decision needs a reason code." };
  }
  if (!Number.isInteger(version)) {
    return { error: "This case could not be identified. Reload and try again." };
  }

  const path = {
    approve: "/api/admin/verifications/{caseId}/approve",
    reject: "/api/admin/verifications/{caseId}/reject",
    revoke: "/api/admin/verifications/{caseId}/revoke",
  }[outcome] as "/api/admin/verifications/{caseId}/approve";

  const result = await (await serverApi()).POST(path, {
    params: { path: { caseId } },
    body: {
      reasonCode,
      version,
      // Only an approval grants a badge, and only a badge has a lifetime. The date the moderator
      // picked becomes the instant that day ends — see expiryInstantFor.
      validThrough: outcome === "approve" ? expiryInstantFor(validThrough) : undefined,
    },
  });

  if (!result.response.ok) {
    return { error: explain(result.response.status) };
  }

  revalidatePath("/verification");
  redirect("/verification");
}

/** Which button was pressed. Anything unrecognised rejects rather than approves. */
function outcomeOf(form: FormData): Outcome {
  const outcome = form.get("outcome");
  return outcome === "approve" || outcome === "revoke" ? outcome : "reject";
}

function explain(status: number): string {
  switch (status) {
    case 409:
      return "Someone decided this case while you were reading it. Reload and look again.";
    case 422:
    case 400:
      return "The API rejected that decision. Check the reason code and, for an approval, the date.";
    case 404:
      return "That case is no longer there.";
    default:
      return "That decision could not be recorded.";
  }
}
