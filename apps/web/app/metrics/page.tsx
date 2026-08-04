import Link from "next/link";
import { redirect } from "next/navigation";
import { serverApi } from "@/src/api/client";
import { accessToken } from "@/src/auth/session";
import { toMetricRows, toOverturnLine, toThroughputRows } from "@/src/metrics/metrics";
import { describeWindow, windowBounds } from "@/src/window";

export const dynamic = "force-dynamic";

/** A month, which is long enough for an overturn rate to mean anything. */
const METRICS_WINDOW_DAYS = 30;

export default async function Metrics({
  searchParams,
}: {
  searchParams: Promise<{ since?: string; until?: string }>;
}) {
  if (!(await accessToken())) {
    redirect("/");
  }

  const { since, until } = await searchParams;
  const window = describeWindow(since, until, new Date(), METRICS_WINDOW_DAYS);
  const bounds = windowBounds(window);

  const result = await (
    await serverApi()
  ).GET("/api/admin/metrics", {
    params: { query: { since: bounds.since, until: bounds.until } },
  });

  if (result.response.status === 401) {
    redirect("/api/auth/expired");
  }
  if (!result.response.ok || !result.data) {
    return (
      <main>
        <p>
          <Link href="/moderation">← Moderation</Link>
        </p>
        <h1>Metrics</h1>
        <p role="alert" data-testid="metrics-error">
          {result.response.status === 403
            ? "This account is not a moderator."
            : "The metrics could not be loaded."}
        </p>
      </main>
    );
  }

  const queue = toMetricRows(result.data);
  const throughput = toThroughputRows(result.data);

  return (
    <main>
      <p>
        <Link href="/moderation">← Moderation</Link>
      </p>
      <h1>Metrics</h1>

      <form action="/metrics" method="get">
        <label htmlFor="since">From</label>
        <input id="since" name="since" type="date" defaultValue={window.since} />
        <label htmlFor="until">To</label>
        <input id="until" name="until" type="date" defaultValue={window.until} />
        <button type="submit">Show</button>
      </form>

      <h2>Right now</h2>
      <table>
        <caption>Queue depth as it stands, whatever window is chosen below</caption>
        <tbody>
          {queue.map((row) => (
            <tr key={row.label} data-testid="metric-row">
              <th scope="row">{row.label}</th>
              <td>{row.value}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <h2>
        {window.since} to {window.until}, both days included
      </h2>
      <table>
        <caption>What was done in the window</caption>
        <tbody>
          {throughput.map((row) => (
            <tr key={row.label} data-testid="metric-row">
              <th scope="row">{row.label}</th>
              <td>{row.value}</td>
            </tr>
          ))}
          {/* The counts lead and the rate follows: a bare percentage over a handful of appeals
              reads as a finding about the platform rather than as the small number it is. */}
          <tr data-testid="metric-row">
            <th scope="row">Appeals</th>
            <td data-testid="overturn-line">{toOverturnLine(result.data)}</td>
          </tr>
        </tbody>
      </table>

      {/* Stated rather than left to be noticed. The audit timeline is where "who did what" is
          answered, for access review; these numbers are deliberately not broken down per person. */}
      <p data-testid="no-names">
        These are platform totals. To see what one moderator has done, use the{" "}
        <Link href="/audit">audit timeline</Link>.
      </p>
    </main>
  );
}
