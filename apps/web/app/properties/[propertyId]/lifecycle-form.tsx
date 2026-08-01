"use client";

import { useActionState } from "react";
import { changeProperty } from "@/src/properties/actions";
import { canActivate, canHide } from "@/src/properties/property";

/**
 * Activate, withdraw, or merge into another record.
 *
 * <p>Only the actions this status allows are offered. Showing a button the API will refuse teaches
 * an administrator to ignore the buttons, which is worse than not offering it.
 */
export function LifecycleForm({
  propertyId,
  version,
  status,
}: {
  propertyId: string;
  version: number;
  status: string | undefined;
}) {
  // Bound rather than wrapped in a closure: React can only submit a form before hydration when its
  // action is a server action, and a click that lands a moment early would otherwise do nothing at
  // all — silently, which is the worst way for a lifecycle action to fail.
  const [state, submit, pending] = useActionState(changeProperty.bind(null, propertyId), undefined);

  const activatable = canActivate(status);
  const hideable = canHide(status);

  if (!activatable && !hideable) {
    return <p>This record is superseded; there is nothing left to change.</p>;
  }

  return (
    <form action={submit}>
      {state?.error ? <p role="alert">{state.error}</p> : null}
      <input type="hidden" name="version" value={version} />

      <p>
        {activatable ? (
          <button type="submit" name="action" value="activate" disabled={pending}>
            Activate
          </button>
        ) : null}
        {hideable ? (
          <button type="submit" name="action" value="hide" disabled={pending}>
            Withdraw
          </button>
        ) : null}
      </p>

      <p>
        <label htmlFor="targetPropertyId">Merge into (property id)</label>
        <input id="targetPropertyId" name="targetPropertyId" />
        <button type="submit" name="action" value="merge" disabled={pending}>
          Merge
        </button>
      </p>
    </form>
  );
}
