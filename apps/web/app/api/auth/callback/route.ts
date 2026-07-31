import { cookies } from "next/headers";
import { NextRequest, NextResponse } from "next/server";
import * as client from "openid-client";
import {
  SESSION_COOKIE,
  TRANSACTION_COOKIE,
  appBaseUrl,
  oidcConfiguration,
} from "@/src/auth/oidc";
import { sessionCookieOptions } from "@/src/auth/session";

/** Completes the authorization-code flow and stores the access token server-side. */
export async function GET(request: NextRequest) {
  const jar = await cookies();
  const transaction = jar.get(TRANSACTION_COOKIE)?.value;
  if (!transaction) {
    // No transaction means this callback was not started by us — a stale tab, or someone feeding
    // us a code. Refuse rather than exchanging it.
    return NextResponse.redirect(new URL("/?error=no_transaction", appBaseUrl()));
  }

  let tokens: client.TokenEndpointResponse;
  try {
    const { codeVerifier, state } = JSON.parse(transaction) as {
      codeVerifier: string;
      state: string;
    };
    tokens = await client.authorizationCodeGrant(
      await oidcConfiguration(),
      new URL(request.url),
      { pkceCodeVerifier: codeVerifier, expectedState: state },
    );
  } catch {
    // The library checks state and the PKCE verifier for us; a failure here is a rejected sign-in,
    // and its detail belongs in the server log rather than in a page. The transaction goes with it:
    // a verifier that has already been presented must not be available for a second attempt, and a
    // retry should start a fresh one.
    const rejected = NextResponse.redirect(new URL("/?error=sign_in_failed", appBaseUrl()));
    rejected.cookies.delete(TRANSACTION_COOKIE);
    return rejected;
  }

  const response = NextResponse.redirect(new URL("/moderation", appBaseUrl()));
  response.cookies.set(
    SESSION_COOKIE,
    tokens.access_token,
    sessionCookieOptions(tokens.expires_in ?? 3600),
  );
  response.cookies.delete(TRANSACTION_COOKIE);
  return response;
}
