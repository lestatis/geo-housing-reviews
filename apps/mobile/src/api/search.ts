import type { ApiResult, GetSuccessBody, MobileApiClient } from "./client";
import { hasString, isRecord, type RuntimeGuard } from "./guards";

/**
 * The property search request.
 *
 * The response type comes from the generated schema, never from here (AGENTS.md §3.7); the guard
 * only checks the fields a result row reads, so a 200 that is not the search response is
 * `malformed` rather than an empty list that looks like "no matches".
 */

/** Search serves one page with no cursor, so a caller cannot ask for a second one. */
export const SEARCH_LIMIT = 20;

export type PropertySearchResponse = GetSuccessBody<"/api/properties/search">;

/**
 * The contract declares `address` and each of its parts nullable: a hit with no address arrives as
 * `"address": null`, a missing part as `"district": null`, and both mean "not recorded". The guard
 * accepts `null` exactly there. `type` is not nullable in the contract — the backend always sets it
 * from the property's enum — so a `null` type is still `malformed`.
 */
function isAbsent(value: unknown): value is null | undefined {
  return value === undefined || value === null;
}

function isAddressSummary(value: unknown): boolean {
  if (!isRecord(value)) {
    return false;
  }
  return (["city", "district", "street", "building"] as const).every(
    (key) => isAbsent(value[key]) || hasString(value, key),
  );
}

function isSearchHit(value: unknown): boolean {
  if (!isRecord(value) || !hasString(value, "propertyId") || !hasString(value, "canonicalName")) {
    return false;
  }
  if (value.type !== undefined && !hasString(value, "type")) {
    return false;
  }
  return isAbsent(value.address) || isAddressSummary(value.address);
}

export const isPropertySearchResponse: RuntimeGuard<PropertySearchResponse> = (
  value,
): value is PropertySearchResponse =>
  isRecord(value) && Array.isArray(value.items) && value.items.every(isSearchHit);

/**
 * Searches the catalogue. `lat`/`lng`/`radiusMeters` are deliberately not sent: this slice has no
 * location UI, and asking for a point the app never uses would be a permission prompt waiting to
 * happen.
 */
export function searchProperties(
  client: MobileApiClient,
  query: string,
  limit: number = SEARCH_LIMIT,
): Promise<ApiResult<PropertySearchResponse>> {
  return client.get("/api/properties/search", isPropertySearchResponse, {
    params: { query: { q: query, limit } },
  });
}
