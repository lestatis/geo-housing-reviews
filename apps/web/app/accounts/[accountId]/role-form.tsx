"use client";

import { useActionState } from "react";
import { changeRole } from "@/src/accounts/actions";

/**
 * Grant or remove administrative access.
 *
 * <p>"Step down" and "remove access" reach the same endpoint and mean different things; offering
 * the wrong label would misdescribe what an administrator is about to do. The API refuses the last
 * administrator either way, and the message says which rule stopped it.
 */
export function RoleForm({
  accountId,
  version,
  role,
  stepDown,
}: {
  accountId: string;
  version: number;
  role: string | undefined;
  stepDown: boolean;
}) {
  // Bound, not wrapped: only a server action can be submitted before the page hydrates.
  const [state, submit, pending] = useActionState(changeRole.bind(null, accountId), undefined);

  return (
    <form action={submit}>
      {state?.error ? <p role="alert">{state.error}</p> : null}
      <input type="hidden" name="version" value={version} />

      {role === "ADMIN" ? (
        <button type="submit" name="role" value="USER" disabled={pending}>
          {stepDown ? "Step down" : "Remove administrative access"}
        </button>
      ) : (
        <button type="submit" name="role" value="ADMIN" disabled={pending}>
          Make administrator
        </button>
      )}
    </form>
  );
}
