import Link from "next/link";
import { redirect } from "next/navigation";
import { serverApi } from "@/src/api/client";
import { accessToken } from "@/src/auth/session";
import { canStepDown, toAccountRow, toRestrictionRow } from "@/src/accounts/account";
import { RoleForm } from "./role-form";
import { RestrictForm } from "./restrict-form";
import { LiftForm } from "./lift-form";
import { meAccountId } from "@/src/auth/me";

export const dynamic = "force-dynamic";

export default async function AccountDetail({
  params,
}: {
  params: Promise<{ accountId: string }>;
}) {
  if (!(await accessToken())) {
    redirect("/");
  }

  const { accountId } = await params;
  const api = await serverApi();
  const result = await api.GET("/api/admin/accounts/{accountId}", {
    params: { path: { accountId } },
  });

  if (result.response.status === 401) {
    redirect("/api/auth/expired");
  }
  if (!result.response.ok || !result.data) {
    return (
      <main>
        <h1>Account</h1>
        <p role="alert">
          {result.response.status === 403
            ? "This account is not a moderator."
            : result.response.status === 404
              ? "No such account."
              : "The account could not be loaded."}
        </p>
        <p>
          <Link href="/accounts">Back to search</Link>
        </p>
      </main>
    );
  }

  const row = toAccountRow(result.data);
  const restrictions = (
    (await api.GET("/api/admin/accounts/{accountId}/restrictions", {
      params: { path: { accountId } },
    })).data?.items ?? []
  ).map(toRestrictionRow);
  const inForce = restrictions.filter((restriction) => restriction.liftable);
  const isSelf = accountId === (await meAccountId());

  return (
    <main>
      <p>
        <Link href="/accounts">← Accounts</Link>
      </p>
      <h1>Account {accountId.slice(0, 8)}</h1>

      <dl>
        <dt>Role</dt>
        <dd data-testid="role">{row.role}</dd>
        <dt>Status</dt>
        <dd data-testid="status">{row.status}</dd>
        <dt>Created</dt>
        <dd>{row.created}</dd>
        {row.closed === "—" ? null : (
          <>
            <dt>Closed</dt>
            <dd>{row.closed}</dd>
          </>
        )}
        <dt>Currently</dt>
        <dd data-testid="standing">
          {inForce.length > 0 ? "Restricted" : "Free to contribute"}
        </dd>
      </dl>

      <h2>Role</h2>
      <RoleForm
        accountId={accountId}
        version={row.version}
        role={result.data.role}
        stepDown={canStepDown(result.data.role, isSelf)}
      />

      <h2>Restrictions</h2>
      {restrictions.length === 0 ? (
        <p>Never restricted.</p>
      ) : (
        <ul>
          {restrictions.map((restriction) => (
            <li key={restriction.restrictionId} data-testid="restriction">
              <strong data-testid="restriction-state">{restriction.state}</strong> ·{" "}
              {restriction.scope} · placed by {restriction.placedBy} · {restriction.started} →{" "}
              {restriction.ends}
              <p>{restriction.reason}</p>
              {restriction.liftable ? (
                <LiftForm accountId={accountId} restrictionId={restriction.restrictionId} />
              ) : null}
            </li>
          ))}
        </ul>
      )}

      {inForce.length === 0 ? <RestrictForm accountId={accountId} /> : null}
    </main>
  );
}
