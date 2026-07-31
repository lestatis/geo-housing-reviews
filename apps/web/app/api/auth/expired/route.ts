import { NextResponse } from "next/server";
import { SESSION_COOKIE, appBaseUrl } from "@/src/auth/oidc";

/**
 * Drops a session the API has already refused.
 *
 * <p>A Server Component cannot clear a cookie, so a page that discovers its token is dead has
 * nowhere to put that fact — and sending the visitor to "/" with the dead cookie still set means
 * "/" sees a session, forwards them back, and the two pages redirect at each other forever. This
 * is the one place that can end it.
 */
export async function GET() {
  const response = NextResponse.redirect(new URL("/?error=session_expired", appBaseUrl()));
  response.cookies.delete(SESSION_COOKIE);
  return response;
}
