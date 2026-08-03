import Link from "next/link";
import { redirect } from "next/navigation";
import { serverApi } from "@/src/api/client";
import { accessToken } from "@/src/auth/session";
import { toQueueRow } from "@/src/moderation/queue";

export const dynamic = "force-dynamic";

export default async function ModerationQueue() {
  if (!(await accessToken())) {
    redirect("/");
  }

  // Deliberately not destructured: the spec documents only a 200 for this operation, so
  // destructuring lets TypeScript correlate `error` and `data` and narrow `response` away
  // entirely. Undocumented error responses are a gap in the OpenAPI document, not here.
  const result = await (await serverApi()).GET("/api/admin/moderation/cases", {});

  if (result.response.status === 401) {
    // The token expired or was rejected. Start again rather than showing an empty queue, which
    // would read as "nothing to moderate" when the truth is "we could not ask" — and go through
    // the route handler, because a Server Component cannot clear the dead cookie and "/" would
    // send us straight back here.
    redirect("/api/auth/expired");
  }
  if (!result.response.ok || !result.data) {
    return (
      <main>
        <h1>Moderation queue</h1>
        <p role="alert">
          {result.response.status === 403
            ? "This account is not a moderator."
            : "The queue could not be loaded."}
        </p>
      </main>
    );
  }

  const rows = (result.data.items ?? []).map(toQueueRow);

  return (
    <main>
      <h1>Moderation queue</h1>
      <nav>
        <Link href="/moderation/appeals">Appeals</Link> ·{" "}
        <Link href="/verification">Verification</Link> ·{" "}
        <Link href="/properties">Properties</Link> · <Link href="/accounts">Accounts</Link>
      </nav>
      <form action="/api/auth/signout" method="post">
        <button type="submit">Sign out</button>
      </form>

      {rows.length === 0 ? (
        <p>No open cases.</p>
      ) : (
        <table>
          <caption>{rows.length} open cases</caption>
          <thead>
            <tr>
              <th scope="col">Target</th>
              <th scope="col">Trigger</th>
              <th scope="col">Risk</th>
              <th scope="col">Status</th>
              <th scope="col">Concerns</th>
              <th scope="col">Opened</th>
              <th scope="col">Assignment</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.caseId} data-testid="case-row">
                <td>
                  <Link href={`/moderation/${row.caseId}`}>{row.target}</Link>
                </td>
                <td>{row.trigger}</td>
                <td>{row.risk}</td>
                <td>{row.status}</td>
                <td>{row.concerns}</td>
                <td>{row.opened}</td>
                <td>{row.assignment}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </main>
  );
}
