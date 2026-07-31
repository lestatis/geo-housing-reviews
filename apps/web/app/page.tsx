import { redirect } from "next/navigation";
import { accessToken } from "@/src/auth/session";

export default async function Home({
  searchParams,
}: {
  searchParams: Promise<{ error?: string }>;
}) {
  const { error } = await searchParams;

  // Only forward an untroubled session. Arriving here *with* an error means the session we hold was
  // just refused, and forwarding it back to the page that refused it is a redirect loop.
  if (!error && (await accessToken())) {
    redirect("/moderation");
  }

  return (
    <main>
      <h1>Geo Housing Reviews — moderation</h1>
      {error === "session_expired" ? (
        <p role="alert">That session has ended. Please sign in again.</p>
      ) : error ? (
        <p role="alert">Sign-in did not complete. Please try again.</p>
      ) : null}
      <p>
        <a href="/api/auth/signin">Sign in</a>
      </p>
    </main>
  );
}
