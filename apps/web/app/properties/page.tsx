import Link from "next/link";
import { redirect } from "next/navigation";
import { serverApi } from "@/src/api/client";
import { accessToken } from "@/src/auth/session";
import { toSearchRow } from "@/src/properties/property";

export const dynamic = "force-dynamic";

export default async function Properties({
  searchParams,
}: {
  searchParams: Promise<{ q?: string }>;
}) {
  if (!(await accessToken())) {
    redirect("/");
  }

  const { q } = await searchParams;
  const query = q?.trim() ?? "";

  // The catalogue has no admin listing — properties are reached by searching for them. Searching
  // for nothing is refused by the API rather than returning everything, so it is not attempted.
  const hits = query
    ? ((await (await serverApi()).GET("/api/properties/search", { params: { query: { q: query } } }))
        .data?.items ?? [])
    : [];
  const rows = hits.map(toSearchRow);

  return (
    <main>
      <p>
        <Link href="/moderation">← Moderation</Link>
      </p>
      <h1>Properties</h1>

      <form action="/properties" method="get">
        <label htmlFor="q">Find a property</label>
        <input id="q" name="q" defaultValue={query} />
        <button type="submit">Search</button>
      </form>

      {query === "" ? (
        <p>Search for a building or address to act on it.</p>
      ) : rows.length === 0 ? (
        <p>Nothing matched “{query}”.</p>
      ) : (
        <ul>
          {rows.map((row) => (
            <li key={row.propertyId} data-testid="property-hit">
              <Link href={`/properties/${row.propertyId}`}>{row.name}</Link>
              {row.distance === "—" ? null : ` · ${row.distance}`}
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}
