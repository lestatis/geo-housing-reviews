"use server";

import { revalidatePath } from "next/cache";
import { serverApi } from "@/src/api/client";

/**
 * Property lifecycle, taken on the server.
 *
 * <p>Each action carries the version the administrator was shown. A merge in particular is close to
 * irreversible for the record that loses — it becomes a tombstone pointing elsewhere — so acting on
 * a stale view is exactly what the version exists to prevent.
 */

export type ActionResult = { error: string } | undefined;

type Lifecycle = "activate" | "hide" | "merge";

/**
 * Bound to its property id by the caller, so the form's action is a server action rather than a
 * client-side closure around one. That is what lets React submit the form before the page has
 * hydrated — wrapped in a closure, an early click is silently swallowed.
 */
export async function changeProperty(
  propertyId: string,
  _previous: ActionResult,
  form: FormData,
): Promise<ActionResult> {
  const action = lifecycleOf(form);
  const version = Number(form.get("version") ?? Number.NaN);
  const targetPropertyId = String(form.get("targetPropertyId") ?? "").trim();

  if (!Number.isInteger(version)) {
    return { error: "This property could not be identified. Reload and try again." };
  }
  if (action === "merge" && !targetPropertyId) {
    // Merging into nothing would silently do something else, or nothing at all.
    return { error: "Say which property this one should be merged into." };
  }
  if (action === "merge" && targetPropertyId === propertyId) {
    return { error: "A property cannot be merged into itself." };
  }

  const path = {
    activate: "/api/admin/properties/{propertyId}/activate",
    hide: "/api/admin/properties/{propertyId}/hide",
    merge: "/api/admin/properties/{propertyId}/merge",
  }[action] as "/api/admin/properties/{propertyId}/activate";

  const result = await (await serverApi()).POST(path, {
    params: { path: { propertyId } },
    body: { version, targetPropertyId: action === "merge" ? targetPropertyId : undefined },
  });

  if (!result.response.ok) {
    return { error: explain(result.response.status) };
  }

  // The route pattern, not the concrete path: for a dynamic segment Next matches cached entries by
  // pattern, and passing the filled-in path silently revalidates nothing. A visitor who arrived
  // through a link would then keep seeing the status and version from before the change, and the
  // next button press would carry a stale version.
  revalidatePath("/properties/[propertyId]", "page");
  return undefined;
}

/** Which button was pressed. Anything unrecognised withdraws rather than publishes. */
function lifecycleOf(form: FormData): Lifecycle {
  const action = form.get("action");
  return action === "activate" || action === "merge" ? action : "hide";
}

function explain(status: number): string {
  switch (status) {
    case 409:
      return "Someone changed this property while you were reading it. Reload and look again.";
    case 404:
      return "That property is no longer there.";
    case 400:
      return "The API rejected that change. Check the target property.";
    default:
      return "That change could not be made.";
  }
}
