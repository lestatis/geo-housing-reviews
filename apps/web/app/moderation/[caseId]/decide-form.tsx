"use client";

import { useActionState } from "react";
import { decideCase } from "@/src/moderation/actions";
import { DECISION_ACTIONS, describeAction } from "@/src/moderation/decision";

/**
 * The decision form.
 *
 * <p>A client component only so a rejected submission can be shown without losing what was typed —
 * the decision itself still happens in the Server Action, which is where the session is. Nothing
 * here holds a token or talks to the API.
 */
export function DecideForm({ caseId }: { caseId: string }) {
  const [state, submit, pending] = useActionState(
    async (_previous: { error: string } | undefined, form: FormData) =>
      decideCase(caseId, form),
    undefined,
  );

  return (
    <form action={submit}>
      {state?.error ? <p role="alert">{state.error}</p> : null}

      <p>
        <label htmlFor="action">What to do</label>
        <select id="action" name="action" defaultValue="">
          <option value="" disabled>
            Choose an action
          </option>
          {DECISION_ACTIONS.map((action) => (
            <option key={action} value={action}>
              {describeAction(action)}
            </option>
          ))}
        </select>
      </p>

      <p>
        <label htmlFor="reasonCode">Reason code</label>
        <input id="reasonCode" name="reasonCode" maxLength={64} />
      </p>

      <p>
        <label htmlFor="publicExplanation">What the author is told</label>
        <textarea id="publicExplanation" name="publicExplanation" rows={3} />
      </p>

      <p>
        <label htmlFor="internalNote">Internal note (moderators only)</label>
        <textarea id="internalNote" name="internalNote" rows={2} />
      </p>

      <button type="submit" disabled={pending}>
        Record decision
      </button>
    </form>
  );
}
