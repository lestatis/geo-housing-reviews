import Link from "next/link";
import { redirect } from "next/navigation";
import { serverApi } from "@/src/api/client";
import { accessToken } from "@/src/auth/session";
import { needsEvidence, toEvidenceRow, toVerificationRow } from "@/src/verification/case";
import { DecideVerificationForm } from "./decide-form";

export const dynamic = "force-dynamic";

export default async function VerificationCaseDetail({
  params,
}: {
  params: Promise<{ caseId: string }>;
}) {
  if (!(await accessToken())) {
    redirect("/");
  }

  const { caseId } = await params;
  const api = await serverApi();
  const result = await api.GET("/api/admin/verifications/{caseId}", {
    params: { path: { caseId } },
  });

  if (result.response.status === 401) {
    redirect("/api/auth/expired");
  }
  if (!result.response.ok || !result.data) {
    return (
      <main>
        <h1>Verification case</h1>
        <p role="alert">
          {result.response.status === 403
            ? "This account is not a moderator."
            : result.response.status === 404
              ? "No such case."
              : "The case could not be loaded."}
        </p>
        <p>
          <Link href="/verification">Back to the queue</Link>
        </p>
      </main>
    );
  }

  const row = toVerificationRow(result.data);

  // Only fetched where a document is what gets read. Asking for evidence on a relationship signal
  // would list nothing and read as a missing document.
  const evidence = needsEvidence(result.data.method)
    ? (
        await api.GET("/api/admin/verifications/{caseId}/evidence", {
          params: { path: { caseId } },
        })
      ).data?.items ?? []
    : [];
  const documents = evidence.map(toEvidenceRow);

  return (
    <main>
      <p>
        <Link href="/verification">← Verification queue</Link>
      </p>
      <h1>Verification case</h1>

      <dl>
        <dt>Account</dt>
        <dd>{row.account}</dd>
        <dt>Property</dt>
        <dd>{row.property}</dd>
        <dt>Method</dt>
        <dd>{row.method}</dd>
        <dt>Claims to be</dt>
        <dd>{row.claim}</dd>
        <dt>Status</dt>
        <dd data-testid="status">{row.status}</dd>
        <dt>Tier granted</dt>
        <dd>{row.tier}</dd>
        <dt>Opened</dt>
        <dd>{row.opened}</dd>
      </dl>

      {needsEvidence(result.data.method) ? (
        <>
          <h2>Evidence</h2>
          <p>
            Opening a document records an audited read. It is checked to confirm the relationship —
            not to certify that anything the review says is true.
          </p>
          {documents.length === 0 ? (
            <p>No document has been uploaded yet.</p>
          ) : (
            <ul>
              {documents.map((document) => (
                <li key={document.evidenceId} data-testid="evidence">
                  {document.contentType} · {document.size} · uploaded {document.uploaded} ·{" "}
                  <span data-testid="evidence-state">{document.state}</span>
                  {document.available ? (
                    <>
                      {" · "}
                      <a
                        href={`/api/evidence/${caseId}/${document.evidenceId}`}
                        rel="noreferrer"
                        data-testid="evidence-link"
                      >
                        Open
                      </a>
                    </>
                  ) : null}
                </li>
              ))}
            </ul>
          )}
        </>
      ) : null}

      <h2>Decide</h2>
      <DecideVerificationForm caseId={caseId} version={row.version} status={row.status} />
    </main>
  );
}
