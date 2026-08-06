import Link from "next/link";
import { redirect } from "next/navigation";
import { serverApi } from "@/src/api/client";
import { accessToken } from "@/src/auth/session";
import { toTimelineRow } from "@/src/audit/timeline";
import { describeWindow, windowBounds, windowFromBounds } from "@/src/window";

export const dynamic = "force-dynamic";

/** A week of history, which is what the page says it shows. */
const AUDIT_WINDOW_DAYS = 7;

export default async function Audit({
  searchParams,
}: {
  searchParams: Promise<{ actor?: string; since?: string; until?: string; cursor?: string }>;
}) {
  if (!(await accessToken())) {
    redirect("/");
  }

  const { actor, since, until, cursor } = await searchParams;
  const continuing = cursor?.trim() || undefined;
  const bounds = continuing
    ? undefined
    : windowBounds(describeWindow(since, until, new Date(), AUDIT_WINDOW_DAYS));

  // A continuation states nothing about its query; the cursor carries it. Sending a window
  // alongside would be this page guessing — and a guess made after midnight is a different window
  // from the one the cursor was issued for, which the API refuses. The old link would have died
  // overnight.
  const result = await (
    await serverApi()
  ).GET("/api/admin/audit", {
    params: {
      query: {
        since: bounds?.since,
        until: bounds?.until,
        actor: continuing ? undefined : actor?.trim() || undefined,
        cursor: continuing,
      },
    },
  });

  if (result.response.status === 401) {
    redirect("/api/auth/expired");
  }
  // `applied` says which window and filter produced this page. Without it the screen cannot label
  // itself honestly, and a mislabelled audit page is worse than a missing one — so it declines to
  // render rather than falling back to the window it would have guessed.
  const applied = result.data?.applied;
  if (!result.response.ok || !result.data || !applied?.since || !applied.until) {
    return (
      <main>
        <p>
          <Link href="/moderation">← Moderation</Link>
        </p>
        <h1>Audit</h1>
        <p role="alert" data-testid="audit-error">
          {problemMessage(result.response.status, result.error)}
        </p>
      </main>
    );
  }

  const rows = (result.data.items ?? []).map(toTimelineRow);
  // What was actually asked, not what this request happened to say. A continuation inherits both
  // window and filter from its cursor, and a page labelled with the question it did not ask is how
  // somebody concludes nothing happened.
  const window = windowFromBounds(applied.since, applied.until);
  const appliedActor = applied.actorAccountId ?? "";
  const older = result.data.nextCursor;
  const olderHref = older
    ? `/audit?${new URLSearchParams({
        since: window.since,
        until: window.until,
        ...(appliedActor ? { actor: appliedActor } : {}),
        cursor: older,
      })}`
    : null;

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
        <input id="actor" name="actor" defaultValue={appliedActor} />
        <button type="submit">Show</button>
      </form>

      {/* Stated, never implied: a filtered timeline that looked unfiltered would let somebody
          conclude nothing happened when they were looking at the wrong week. Both dates are
          included — "to 4 August" shows the whole of the 4th. */}
      <p data-testid="window">
        {window.since} to {window.until}, both days included
        {appliedActor ? `, account ${appliedActor.slice(0, 8)} only` : ", everyone"}
        {cursor ? ", continued" : ""}
      </p>

      {rows.length === 0 ? (
        <p data-testid="empty">Nothing was recorded in this window.</p>
      ) : (
        <table>
          {/* Says whether this is all of it. "42 entries" over a page that stopped at the limit
              reads as a complete answer, which is how somebody concludes nothing else happened. */}
          <caption data-testid="page-summary">
            {rows.length} entries, newest first
            {olderHref ? " — more remain" : " — this is the end of the window"}
          </caption>
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

      {olderHref ? (
        <p>
          <Link href={olderHref} data-testid="older">
            Show older entries
          </Link>
        </p>
      ) : null}
    </main>
  );
}

/** Enough of a Problem Details body to point at the field that is wrong. */
type Problem = { code?: string; fieldErrors?: { field?: string; code?: string }[] };

const FIELD_MESSAGES: Record<string, string> = {
  actor: "That account id is not an id. Paste the whole identifier, or leave it blank for everyone.",
  until: "The end of the window is before its start. Swap the two dates.",
  cursor: "That link no longer works. Start again from the top of the window.",
};

/**
 * What went wrong, in the reader's terms.
 *
 * <p>A mistyped account id used to reach the API as an unhandled failure and come back a 500, which
 * the screen reported as "the timeline could not be loaded" — telling an administrator the audit
 * log had broken when they had made a typo.
 */
function problemMessage(status: number, error: unknown): string {
  if (status === 403) {
    return "This account is not a moderator.";
  }
  const field = (error as Problem | undefined)?.fieldErrors?.[0]?.field;
  if (status === 400 && field && FIELD_MESSAGES[field]) {
    return FIELD_MESSAGES[field];
  }
  return "The timeline could not be loaded.";
}
