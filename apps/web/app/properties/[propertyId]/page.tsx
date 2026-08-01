import Link from "next/link";
import { redirect } from "next/navigation";
import { serverApi } from "@/src/api/client";
import { accessToken } from "@/src/auth/session";
import { toPropertyRow } from "@/src/properties/property";
import { LifecycleForm } from "./lifecycle-form";

export const dynamic = "force-dynamic";

export default async function PropertyDetail({
  params,
}: {
  params: Promise<{ propertyId: string }>;
}) {
  if (!(await accessToken())) {
    redirect("/");
  }

  const { propertyId } = await params;
  const result = await (
    await serverApi()
  ).GET("/api/properties/{propertyId}", { params: { path: { propertyId } } });

  if (result.response.status === 401) {
    redirect("/api/auth/expired");
  }
  if (!result.response.ok || !result.data) {
    return (
      <main>
        <h1>Property</h1>
        <p role="alert">
          {result.response.status === 404 ? "No such property." : "It could not be loaded."}
        </p>
        <p>
          <Link href="/properties">Back to search</Link>
        </p>
      </main>
    );
  }

  const row = toPropertyRow(result.data);

  return (
    <main>
      <p>
        <Link href="/properties">← Properties</Link>
      </p>
      <h1>{row.name}</h1>
      <dl>
        <dt>Type</dt>
        <dd>{row.type}</dd>
        <dt>Status</dt>
        <dd data-testid="property-status">{row.status}</dd>
        <dt>Address</dt>
        <dd>{row.address}</dd>
        {row.mergedInto === "—" ? null : (
          <>
            <dt>Merged into</dt>
            <dd data-testid="merged-into">{row.mergedInto}</dd>
          </>
        )}
      </dl>

      <h2>Lifecycle</h2>
      <LifecycleForm propertyId={propertyId} version={row.version} status={result.data.status} />
    </main>
  );
}
