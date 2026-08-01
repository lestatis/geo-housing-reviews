import Link from "next/link";
import { redirect } from "next/navigation";
import { serverApi } from "@/src/api/client";
import { accessToken } from "@/src/auth/session";
import { toAppealRow } from "@/src/moderation/appeal";
import { HearAppealForm } from "./hear-appeal-form";

export const dynamic = "force-dynamic";

export default async function Appeals() {
  if (!(await accessToken())) {
    redirect("/");
  }

  const result = await (await serverApi()).GET("/api/admin/moderation/appeals", {});

  if (result.response.status === 401) {
    redirect("/api/auth/expired");
  }
  if (!result.response.ok || !result.data) {
    return (
      <main>
        <h1>Appeals</h1>
        <p role="alert">
          {result.response.status === 403
            ? "This account is not a moderator."
            : "The appeals queue could not be loaded."}
        </p>
      </main>
    );
  }

  const rows = (result.data.items ?? []).map(toAppealRow);

  return (
    <main>
      <p>
        <Link href="/moderation">← Queue</Link>
      </p>
      <h1>Appeals</h1>

      {rows.length === 0 ? (
        <p>No appeals waiting.</p>
      ) : (
        <ul>
          {rows.map((row) => (
            <li key={row.appealId} data-testid="appeal">
              <h2>{row.target}</h2>
              <dl>
                <dt>Decision under appeal</dt>
                <dd data-testid="contested">{row.contested}</dd>
                <dt>The author was told</dt>
                <dd>{row.toldTheAuthor}</dd>
                <dt>Decided by</dt>
                <dd data-testid="original-decider">{row.originalDecider}</dd>
                <dt>Filed</dt>
                <dd>{row.filed}</dd>
              </dl>
              <blockquote data-testid="appeal-text">{row.appealText}</blockquote>
              <p>
                <Link href={`/moderation/${row.caseId}`}>Read the case</Link>
              </p>
              <HearAppealForm appealId={row.appealId} />
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}
