"use server";

import { revalidatePath } from "next/cache";
import { serverApi } from "@/src/api/client";

/**
 * Role changes and restrictions, taken on the server like every other write in this app.
 *
 * <p>Bound to the account id by the caller, so each form's action is a server action rather than a
 * client-side closure around one — a wrapped action cannot be submitted before the page hydrates,
 * and a click that lands first is swallowed without a word.
 */

export type ActionResult = { error: string } | undefined;

export async function changeRole(
  accountId: string,
  _previous: ActionResult,
  form: FormData,
): Promise<ActionResult> {
  const role = String(form.get("role") ?? "");
  const version = Number(form.get("version") ?? Number.NaN);

  if (role !== "ADMIN" && role !== "USER") {
    return { error: "Choose a role." };
  }
  if (!Number.isInteger(version)) {
    return { error: "This account could not be identified. Reload and try again." };
  }

  const result = await (await serverApi()).PATCH("/api/admin/accounts/{accountId}/role", {
    params: { path: { accountId } },
    body: { role, version },
  });

  if (!result.response.ok) {
    return { error: explainRole(result.response.status) };
  }

  revalidatePath("/accounts/[accountId]", "page");
  return undefined;
}

export async function restrictAccount(
  accountId: string,
  _previous: ActionResult,
  form: FormData,
): Promise<ActionResult> {
  const reason = String(form.get("reason") ?? "").trim();

  if (!reason) {
    // The reason is what the restricted person can be told, and therefore what they can answer.
    // The API refuses a blank one too; saying so here means learning before submitting.
    return { error: "Say why this account is being restricted." };
  }

  const result = await (await serverApi()).POST("/api/admin/accounts/{accountId}/restrictions", {
    params: { path: { accountId } },
    body: { scope: "ACCOUNT_WIDE", reason },
  });

  if (!result.response.ok) {
    return { error: explainRestriction(result.response.status) };
  }

  revalidatePath("/accounts/[accountId]", "page");
  return undefined;
}

export async function liftRestriction(
  accountId: string,
  _previous: ActionResult,
  form: FormData,
): Promise<ActionResult> {
  const restrictionId = String(form.get("restrictionId") ?? "");
  if (!restrictionId) {
    return { error: "That restriction could not be identified. Reload and try again." };
  }

  const result = await (
    await serverApi()
  ).POST("/api/admin/accounts/{accountId}/restrictions/{restrictionId}/lift", {
    params: { path: { accountId, restrictionId } },
  });

  if (!result.response.ok) {
    return { error: explainRestriction(result.response.status) };
  }

  revalidatePath("/accounts/[accountId]", "page");
  return undefined;
}

function explainRole(status: number): string {
  switch (status) {
    case 409:
      return "This is the only administrator, or the account changed while you were reading it. Reload and look again.";
    case 404:
      return "That account is no longer there.";
    default:
      return "That role change could not be made.";
  }
}

function explainRestriction(status: number): string {
  switch (status) {
    case 409:
      return "This account already has a restriction in force, or the one you are lifting has already ended.";
    case 404:
      return "That account or restriction is no longer there.";
    case 400:
      return "The API rejected that restriction. Check the reason.";
    default:
      return "That change could not be made.";
  }
}
