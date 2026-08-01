import Link from "next/link";
import { redirect } from "next/navigation";
import { serverApi } from "@/src/api/client";
import { accessToken } from "@/src/auth/session";
import { toVerificationRow } from "@/src/verification/case";

export const dynamic = "force-dynamic";

export default async function VerificationQueue() {
  if (!(await accessToken())) {
    redirect("/");
  }

  const result = await (await serverApi()).GET("/api/admin/verifications", {});

  if (result.response.status === 401) {
    redirect("/api/auth/expired");
  }
  if (!result.response.ok || !result.data) {
    return (
      <main>
        <h1>Verification queue</h1>
        <p role="alert">
          {result.response.status === 403
            ? "This account is not a moderator."
            : "The verification queue could not be loaded."}
        </p>
      </main>
    );
  }

  const rows = (result.data.items ?? []).map(toVerificationRow);

  return (
    <main>
      <p>
        <Link href="/moderation">← Moderation</Link>
      </p>
      <h1>Verification queue</h1>

      {rows.length === 0 ? (
        <p>Nothing waiting to be verified.</p>
      ) : (
        <table>
          <caption>{rows.length} cases awaiting a decision</caption>
          <thead>
            <tr>
              <th scope="col">Account</th>
              <th scope="col">Property</th>
              <th scope="col">Method</th>
              <th scope="col">Claim</th>
              <th scope="col">Opened</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.caseId} data-testid="verification-row">
                <td>
                  <Link href={`/verification/${row.caseId}`}>{row.account}</Link>
                </td>
                <td>{row.property}</td>
                <td>{row.method}</td>
                <td>{row.claim}</td>
                <td>{row.opened}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </main>
  );
}
