"use client";

import { useActionState } from "react";
import { liftRestriction } from "@/src/accounts/actions";

/** Ends a restriction now. The record stays; only the window closes. */
export function LiftForm({
  accountId,
  restrictionId,
}: {
  accountId: string;
  restrictionId: string;
}) {
  const [state, submit, pending] = useActionState(liftRestriction.bind(null, accountId), undefined);

  return (
    <form action={submit}>
      {state?.error ? <p role="alert">{state.error}</p> : null}
      <input type="hidden" name="restrictionId" value={restrictionId} />
      <button type="submit" disabled={pending}>
        Lift
      </button>
    </form>
  );
}
