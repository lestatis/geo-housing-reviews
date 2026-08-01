import Link from "next/link";
import { redirect } from "next/navigation";
import { serverApi } from "@/src/api/client";
import { accessToken } from "@/src/auth/session";
import { toConcernRow, toDecisionRow } from "@/src/moderation/case";
import { toQueueRow } from "@/src/moderation/queue";
import { DecideForm } from "./decide-form";

export const dynamic = "force-dynamic";

export default async function CaseDetail({ params }: { params: Promise<{ caseId: string }> }) {
  if (!(await accessToken())) {
    redirect("/");
  }

  const { caseId } = await params;
  const result = await (
    await serverApi()
  ).GET("/api/admin/moderation/cases/{caseId}", { params: { path: { caseId } } });

  if (result.response.status === 401) {
    redirect("/api/auth/expired");
  }
  if (!result.response.ok || !result.data) {
    return (
      <main>
        <h1>Case</h1>
        <p role="alert">
          {result.response.status === 403
            ? "This account is not a moderator."
            : result.response.status === 404
              ? "No such case."
              : "The case could not be loaded."}
        </p>
        <p>
          <Link href="/moderation">Back to the queue</Link>
        </p>
      </main>
    );
  }

  const summary = toQueueRow(result.data.summary ?? {});
  const concerns = (result.data.concerns ?? []).map(toConcernRow);
  const decisions = (result.data.decisions ?? []).map(toDecisionRow);

  return (
    <main>
      <p>
        <Link href="/moderation">← Queue</Link>
      </p>
      <h1>{summary.target}</h1>
      <dl>
        <dt>Status</dt>
        <dd>{summary.status}</dd>
        <dt>Risk</dt>
        <dd>{summary.risk}</dd>
        <dt>Opened</dt>
        <dd>{summary.opened}</dd>
        <dt>Assignment</dt>
        <dd>{summary.assignment}</dd>
      </dl>

      <h2>{summary.concerns} raised</h2>
      {concerns.length === 0 ? (
        <p>This case was not opened by a report.</p>
      ) : (
        <ul>
          {concerns.map((concern, index) => (
            <li key={index} data-testid="concern">
              <strong>{concern.category}</strong> · {concern.raised}
              <p>{concern.description}</p>
            </li>
          ))}
        </ul>
      )}

      <h2>Decisions</h2>
      {decisions.length === 0 ? (
        <p>Nothing decided yet.</p>
      ) : (
        <ol>
          {decisions.map((decision, index) => (
            <li key={index} data-testid="decision">
              <strong>{decision.action}</strong> · {decision.reasonCode} · {decision.decided} · by{" "}
              {decision.decidedBy}
              <p data-testid="public-explanation">Told the author: {decision.publicExplanation}</p>
              <p data-testid="internal-note">Internal note: {decision.internalNote}</p>
            </li>
          ))}
        </ol>
      )}

      <h2>Decide</h2>
      <DecideForm caseId={caseId} />
    </main>
  );
}
