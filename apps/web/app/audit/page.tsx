import Link from "next/link";
import { redirect } from "next/navigation";
import { serverApi } from "@/src/api/client";
import { accessToken } from "@/src/auth/session";
import { describeWindow, toTimelineRow } from "@/src/audit/timeline";

export const dynamic = "force-dynamic";

export default async function Audit({
  searchParams,
}: {
  searchParams: Promise<{ actor?: string; since?: string; until?: string }>;
}) {
  if (!(await accessToken())) {
    redirect("/");
  }

  const { actor, since, until } = await searchParams;
  const window = describeWindow(since, until, new Date());
  const trimmedActor = actor?.trim() ?? "";

  const result = await (
    await serverApi()
  ).GET("/api/admin/audit", {
    params: {
      query: {
        since: `${window.since}T00:00:00Z`,
        until: `${window.until}T23:59:59Z`,
        actor: trimmedActor || undefined,
      },
    },
  });

  if (result.response.status === 401) {
    redirect("/api/auth/expired");
  }
  if (!result.response.ok || !result.data) {
    return (
      <main>
        <h1>Audit</h1>
        <p role="alert">
          {result.response.status === 403
            ? "This account is not a moderator."
            : "The timeline could not be loaded."}
        </p>
      </main>
    );
  }

  const rows = (result.data.items ?? []).map(toTimelineRow);

  return (
    <main>
      <p>
        <Link href="/moderation">← Moderation</Link>
      </p>
      <h1>Audit</h1>

      <form action="/audit" method="get">
        <label htmlFor="since">From</label>
        <input id="since" name="since" type="date" defaultValue={window.since} />
        <label htmlFor="until">To</label>
        <input id="until" name="until" type="date" defaultValue={window.until} />
        <label htmlFor="actor">Account id (optional)</label>
        <input id="actor" name="actor" defaultValue={trimmedActor} />
        <button type="submit">Show</button>
      </form>

      {/* Stated, never implied: a filtered timeline that looked unfiltered would let somebody
          conclude nothing happened when they were looking at the wrong week. */}
      <p data-testid="window">
        {window.since} to {window.until}
        {trimmedActor ? `, account ${trimmedActor.slice(0, 8)} only` : ", everyone"}
      </p>

      {rows.length === 0 ? (
        <p data-testid="empty">Nothing was recorded in this window.</p>
      ) : (
        <table>
          <caption>{rows.length} entries, newest first</caption>
          <thead>
            <tr>
              <th scope="col">When</th>
              <th scope="col">Who</th>
              <th scope="col">Where</th>
              <th scope="col">Action</th>
              <th scope="col">On</th>
              <th scope="col">Outcome</th>
              <th scope="col">Reason</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row, index) => (
              <tr key={index} data-testid="audit-row">
                <td>{row.when}</td>
                <td>{row.actor}</td>
                <td>{row.where}</td>
                <td>{row.action}</td>
                <td>{row.subject}</td>
                <td>{row.outcome}</td>
                <td>{row.reason}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </main>
  );
}
