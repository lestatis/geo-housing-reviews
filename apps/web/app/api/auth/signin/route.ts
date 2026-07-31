import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import * as client from "openid-client";
import {
  TRANSACTION_COOKIE,
  oidcConfiguration,
  redirectUri,
} from "@/src/auth/oidc";
import { sessionCookieOptions } from "@/src/auth/session";

/** Starts an authorization-code flow with PKCE. */
export async function GET() {
  const config = await oidcConfiguration();

  const codeVerifier = client.randomPKCECodeVerifier();
  const state = client.randomState();

  // The verifier and state must survive the round trip to the provider without being readable or
  // forgeable by the browser, which is what makes the callback's checks worth making.
  (await cookies()).set(
    TRANSACTION_COOKIE,
    JSON.stringify({ codeVerifier, state }),
    sessionCookieOptions(600),
  );

  const authorizationUrl = client.buildAuthorizationUrl(config, {
    redirect_uri: redirectUri(),
    scope: "openid email",
    code_challenge: await client.calculatePKCECodeChallenge(codeVerifier),
    code_challenge_method: "S256",
    state,
  });

  redirect(authorizationUrl.href);
}
