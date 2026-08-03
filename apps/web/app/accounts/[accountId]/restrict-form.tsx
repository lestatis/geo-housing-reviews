"use client";

import { useActionState } from "react";
import { restrictAccount } from "@/src/accounts/actions";

/**
 * Place a restriction.
 *
 * <p>No end date is offered. A restriction placed here lasts until an administrator lifts it — the
 * same shape as one placed by a moderation decision. Offering a window would suggest the platform
 * tracks and enforces it as policy, when what actually happens is that it quietly expires.
 */
export function RestrictForm({ accountId }: { accountId: string }) {
  const [state, submit, pending] = useActionState(restrictAccount.bind(null, accountId), undefined);

  return (
    <form action={submit}>
      {state?.error ? <p role="alert">{state.error}</p> : null}
      <p>
        <label htmlFor="reason">Why (the account can be told this)</label>
        <textarea id="reason" name="reason" rows={2} />
      </p>
      <button type="submit" disabled={pending}>
        Restrict this account
      </button>
    </form>
  );
}
