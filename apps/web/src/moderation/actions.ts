"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { serverApi } from "@/src/api/client";
import { requiresPublicExplanation } from "./decision";

/**
 * Deciding happens on the server.
 *
 * <p>A Server Action runs where the session cookie already lives, so a decision never needs the
 * access token in the browser and the API opens no CORS surface for this origin. It is also the
 * only way this app writes anything — there is no client-side fetch to the API at all.
 */

export type ActionResult = { error: string } | undefined;

export async function decideCase(caseId: string, form: FormData): Promise<ActionResult> {
  const action = String(form.get("action") ?? "");
  const reasonCode = String(form.get("reasonCode") ?? "").trim();
  const publicExplanation = String(form.get("publicExplanation") ?? "").trim();
  const internalNote = String(form.get("internalNote") ?? "").trim();

  if (!action) {
    return { error: "Choose what to do with this content." };
  }
  if (!reasonCode) {
    return { error: "Every decision needs a reason code." };
  }
  if (requiresPublicExplanation(action) && !publicExplanation) {
    // The server enforces this too. Saying it here means a moderator learns before they submit
    // that a takedown owes the author a reason, rather than after.
    return { error: "This action takes something away, so the author must be told why." };
  }

  const result = await (
    await serverApi()
  ).POST("/api/admin/moderation/cases/{caseId}/decide", {
    params: { path: { caseId } },
    body: {
      action,
      reasonCode,
      publicExplanation: publicExplanation || undefined,
      internalNote: internalNote || undefined,
    },
  });

  if (!result.response.ok) {
    return { error: explain(result.response.status) };
  }

  revalidatePath("/moderation");
  redirect("/moderation");
}

export async function decideAppeal(appealId: string, form: FormData): Promise<ActionResult> {
  const outcome = String(form.get("outcome") ?? "");
  const explanation = String(form.get("explanation") ?? "").trim();

  if (outcome !== "UPHOLD" && outcome !== "OVERTURN") {
    return { error: "Choose whether the decision stands or is overturned." };
  }
  if (!explanation) {
    // An appeal answered without reasons is the same non-answer the appeal was filed against.
    return { error: "An appeal outcome has to be explained to the person who filed it." };
  }

  const result = await (
    await serverApi()
  ).POST("/api/admin/moderation/appeals/{appealId}/decide", {
    params: { path: { appealId } },
    body: { outcome, explanation },
  });

  if (!result.response.ok) {
    return { error: explainAppeal(result.response.status) };
  }

  revalidatePath("/moderation/appeals");
  redirect("/moderation/appeals");
}

function explain(status: number): string {
  switch (status) {
    case 409:
      return "Someone changed this content while you were reading it. Reload and look again.";
    case 400:
      return "The API rejected that decision. Check the action and reason code.";
    default:
      return "That decision could not be recorded.";
  }
}

function explainAppeal(status: number): string {
  switch (status) {
    case 403:
      return "An appeal cannot be heard by the moderator whose decision it challenges.";
    case 409:
      return "The content could not be put back, so the appeal is still open. Uphold it with an explanation, or try again.";
    default:
      return explain(status);
  }
}
