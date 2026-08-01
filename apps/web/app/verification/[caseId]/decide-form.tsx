"use client";

import { useActionState } from "react";
import { decideVerification } from "@/src/verification/actions";

/**
 * Approve, reject, or revoke a badge already granted.
 *
 * <p>The version the moderator was shown travels with the decision, so a case decided by someone
 * else in the meantime is refused rather than silently overwritten.
 */
export function DecideVerificationForm({
  caseId,
  version,
  status,
}: {
  caseId: string;
  version: number;
  status: string;
}) {
  // Bound, not wrapped: only a server action can be submitted before the page hydrates.
  const [state, submit, pending] = useActionState(decideVerification.bind(null, caseId), undefined);

  return (
    <form action={submit}>
      {state?.error ? <p role="alert">{state.error}</p> : null}
      <input type="hidden" name="version" value={version} />

      <p>
        <label htmlFor="reasonCode">Reason code</label>
        <input id="reasonCode" name="reasonCode" maxLength={64} />
      </p>
      <p>
        <label htmlFor="validThrough">Valid through (approval only)</label>
        <input id="validThrough" name="validThrough" type="date" />
      </p>
      <p>
        <button type="submit" name="outcome" value="approve" disabled={pending}>
          Approve
        </button>
        <button type="submit" name="outcome" value="reject" disabled={pending}>
          Reject
        </button>
        {/* Revoking is for a badge already granted; offering it on a pending case would only
            produce a refusal from the API. */}
        {status === "Approved" ? (
          <button type="submit" name="outcome" value="revoke" disabled={pending}>
            Revoke the badge
          </button>
        ) : null}
      </p>
    </form>
  );
}
