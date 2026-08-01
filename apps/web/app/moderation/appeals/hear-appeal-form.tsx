"use client";

import { useActionState } from "react";
import { decideAppeal } from "@/src/moderation/actions";

/**
 * Uphold or overturn, with reasons.
 *
 * <p>The explanation is not optional in either direction. An appeal answered without one is the
 * same non-answer the appeal was filed against, and an overturn that puts content back still owes
 * the author an account of why the first decision was wrong.
 */
export function HearAppealForm({ appealId }: { appealId: string }) {
  const [state, submit, pending] = useActionState(
    async (_previous: { error: string } | undefined, form: FormData) =>
      decideAppeal(appealId, form),
    undefined,
  );

  return (
    <form action={submit}>
      {state?.error ? <p role="alert">{state.error}</p> : null}

      <p>
        <label htmlFor={`explanation-${appealId}`}>Why</label>
        <textarea id={`explanation-${appealId}`} name="explanation" rows={2} />
      </p>
      <p>
        <button type="submit" name="outcome" value="UPHOLD" disabled={pending}>
          Uphold the decision
        </button>
        <button type="submit" name="outcome" value="OVERTURN" disabled={pending}>
          Overturn and restore
        </button>
      </p>
    </form>
  );
}
