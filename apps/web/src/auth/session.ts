import { cookies } from "next/headers";
import { SESSION_COOKIE } from "./oidc";

/**
 * The access token lives in an httpOnly cookie and is read only on the server.
 *
 * <p>It is never sent to the browser and never reaches client-side JavaScript: pages call the API
 * from the server, so a cross-site script has nothing to steal and the API needs no CORS opening
 * for the admin origin.
 */
export async function accessToken(): Promise<string | undefined> {
  return (await cookies()).get(SESSION_COOKIE)?.value;
}

export function sessionCookieOptions(maxAgeSeconds: number) {
  return {
    httpOnly: true,
    sameSite: "lax" as const,
    // Loopback development is plain HTTP; anything else must be HTTPS or the cookie is not sent.
    secure: process.env.NODE_ENV === "production",
    path: "/",
    maxAge: maxAgeSeconds,
  };
}
