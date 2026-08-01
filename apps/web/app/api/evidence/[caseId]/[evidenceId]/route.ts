import { NextResponse } from "next/server";
import { apiBaseUrl } from "@/src/api/client";
import { accessToken } from "@/src/auth/session";

/**
 * One moderator-authorised, audited read of a verification document.
 *
 * <p>This proxies rather than redirects, and that is deliberate on both sides. The API deliberately
 * serves the bytes instead of handing out a reusable storage URL, and the admin app deliberately
 * keeps its access token server-side — so there is no arrangement in which the browser could fetch
 * the document itself. Every view therefore travels through the API and lands in its access audit,
 * which is the property that matters for evidence (SECURITY_PRIVACY, ADR-0008).
 *
 * <p>Nothing is stored. The body is streamed straight through, `no-store` is asserted here rather
 * than merely forwarded, and the URL is useless without this app's session cookie — being guessable
 * is not the same as being reachable.
 */
export async function GET(
  _request: Request,
  context: { params: Promise<{ caseId: string; evidenceId: string }> },
) {
  const token = await accessToken();
  if (!token) {
    return NextResponse.redirect(new URL("/", process.env.APP_BASE_URL ?? "http://localhost:3000"));
  }

  const { caseId, evidenceId } = await context.params;
  const upstream = await fetch(
    `${apiBaseUrl()}/api/admin/verifications/${encodeURIComponent(caseId)}/evidence/${encodeURIComponent(evidenceId)}`,
    { headers: { authorization: `Bearer ${token}` }, cache: "no-store" },
  );

  if (!upstream.ok || !upstream.body) {
    // Nothing about why: whether a document exists, was deleted by retention, or belongs to a case
    // this account may not read are all the same answer to whoever is asking.
    return new NextResponse("Evidence is not available.", {
      status: upstream.status === 200 ? 502 : upstream.status,
      headers: { "cache-control": "no-store" },
    });
  }

  return new NextResponse(upstream.body, {
    status: 200,
    headers: {
      "content-type": upstream.headers.get("content-type") ?? "application/octet-stream",
      // Kept from the API's own response: a document is downloaded, never rendered inline where a
      // crafted file could execute against this origin.
      "content-disposition":
        upstream.headers.get("content-disposition") ?? 'attachment; filename="evidence"',
      "x-content-type-options": "nosniff",
      "cache-control": "no-store, no-cache, must-revalidate, private",
      // A document must not be the referrer for anything, and must not be framed.
      "referrer-policy": "no-referrer",
      "content-security-policy": "default-src 'none'; sandbox",
    },
  });
}
