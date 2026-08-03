import Link from "next/link";
import { redirect } from "next/navigation";
import { serverApi } from "@/src/api/client";
import { accessToken } from "@/src/auth/session";

export const dynamic = "force-dynamic";

export default async function Accounts({
  searchParams,
}: {
  searchParams: Promise<{ pseudonym?: string }>;
}) {
  if (!(await accessToken())) {
    redirect("/");
  }

  const { pseudonym } = await searchParams;
  const query = pseudonym?.trim() ?? "";

  // An account is reached by the pseudonym a moderator can see on a review — there is no listing,
  // and there should not be one: browsing accounts is not something moderating content requires.
  const found = query
    ? await (
        await serverApi()
      ).GET("/api/admin/accounts", { params: { query: { pseudonym: query } } })
    : undefined;

  if (found?.response.status === 401) {
    redirect("/api/auth/expired");
  }

  return (
    <main>
      <p>
        <Link href="/moderation">← Moderation</Link>
      </p>
      <h1>Accounts</h1>

      <form action="/accounts" method="get">
        <label htmlFor="pseudonym">Find an account by pseudonym</label>
        <input id="pseudonym" name="pseudonym" defaultValue={query} />
        <button type="submit">Find</button>
      </form>

      {query === "" ? (
        <p>Enter the pseudonym shown on a review.</p>
      ) : found?.data?.accountId ? (
        <p>
          <Link href={`/accounts/${found.data.accountId}`} data-testid="account-hit">
            {query}
          </Link>
        </p>
      ) : (
        <p role="alert">No account uses “{query}”.</p>
      )}
    </main>
  );
}
