import { NextResponse } from "next/server";
import { SESSION_COOKIE, appBaseUrl } from "@/src/auth/oidc";

/** Drops the local session. POST only, so a link or image cannot sign someone out. */
export async function POST() {
  const response = NextResponse.redirect(new URL("/", appBaseUrl()), { status: 303 });
  response.cookies.delete(SESSION_COOKIE);
  return response;
}
