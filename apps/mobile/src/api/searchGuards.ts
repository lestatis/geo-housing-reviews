import type { components } from "./generated/schema";
import { hasString, isRecord, type RuntimeGuard } from "./guards";

type GeneratedSearchResponse = components["schemas"]["PropertySearchResponse"];
type GeneratedSearchHit = components["schemas"]["PropertySearchHitResponse"];

/**
 * A conforming hit, with the keys the guard proves present made required. The address type stays
 * the generated one (`AddressSummaryView | null`); the guard never redefines the contract.
 */
export type SearchHit = Omit<
  GeneratedSearchHit,
  "propertyId" | "canonicalName" | "type" | "address"
> &
  Required<
    Pick<GeneratedSearchHit, "propertyId" | "canonicalName" | "type" | "address">
  >;

/**
 * The four parts are required by the backend even when their values are `null`; deriving this from
 * the generated nullable object keeps a contract rename a compile error.
 */
type SearchAddressSummary = Required<NonNullable<GeneratedSearchHit["address"]>>;

export type SearchResponse = Omit<GeneratedSearchResponse, "items"> & { items: SearchHit[] };

const ADDRESS_PART_KEYS = ["city", "district", "street", "building"] as const;

function hasNullableString(record: Record<string, unknown>, key: string): boolean {
  return (
    Object.prototype.hasOwnProperty.call(record, key) &&
    (record[key] === null || typeof record[key] === "string")
  );
}

function isAddressSummary(value: unknown): value is SearchAddressSummary {
  return (
    isRecord(value) && ADDRESS_PART_KEYS.every((key) => hasNullableString(value, key))
  );
}

function isSearchHit(value: unknown): value is SearchHit {
  if (
    !isRecord(value) ||
    !hasString(value, "propertyId") ||
    !hasString(value, "canonicalName") ||
    !hasString(value, "type") ||
    !Object.prototype.hasOwnProperty.call(value, "address")
  ) {
    return false;
  }
  return value.address === null || isAddressSummary(value.address);
}

/**
 * Checks the fields the results screen actually reads.
 *
 * A missing `address` key is malformed rather than "no address": the contract represents an absent
 * address row as `null`, and accepting both spellings would hide a projection regression. `score`
 * and `distanceMeters` are intentionally not validated here because the UI never reads them.
 */
export const isSearchResponse: RuntimeGuard<SearchResponse> = (
  value,
): value is SearchResponse =>
  isRecord(value) && Array.isArray(value.items) && value.items.every(isSearchHit);
