import { humanise } from "@/src/format";
import type { components } from "@/src/api/generated/schema";

export type SearchHit = components["schemas"]["PropertySearchHitResponse"];
export type Property = components["schemas"]["PropertyResponse"];

/**
 * A search result. Deliberately thin: the search response carries neither status nor version, and
 * a lifecycle action needs both — so a hit links to the property rather than offering a button it
 * would have to guess the arguments for.
 */
export type SearchRow = {
  propertyId: string;
  name: string;
  distance: string;
};

export function toSearchRow(hit: SearchHit): SearchRow {
  return {
    propertyId: hit.propertyId ?? "",
    name: hit.canonicalName ?? "—",
    distance: formatDistance(hit.distanceMeters),
  };
}

export type PropertyRow = {
  propertyId: string;
  name: string;
  type: string;
  status: string;
  address: string;
  mergedInto: string;
  version: number;
};

export function toPropertyRow(property: Property): PropertyRow {
  return {
    propertyId: property.propertyId ?? "",
    name: property.canonicalName ?? "—",
    type: humanise(property.type),
    status: humanise(property.status),
    address: formatAddress(property.address),
    mergedInto: property.mergedIntoPropertyId ?? "—",
    // Carried so a lifecycle action names the version the administrator was looking at.
    version: property.version ?? 0,
  };
}

/**
 * Whether activating this property is a real option.
 *
 * <p>A merged record is a tombstone pointing at the one that survived; bringing it back would
 * split the catalogue entry a merge existed to join.
 */
export function canActivate(status: string | undefined): boolean {
  return status === "DRAFT" || status === "HIDDEN";
}

/** Whether hiding it is a real option — only what is still visible can be withdrawn. */
export function canHide(status: string | undefined): boolean {
  return status === "DRAFT" || status === "ACTIVE";
}

function formatAddress(address: Property["address"]): string {
  if (!address) {
    return "—";
  }
  const parts = [address.street, address.city].filter(Boolean);
  return parts.length > 0 ? parts.join(", ") : "—";
}

function formatDistance(meters: number | undefined): string {
  if (meters === undefined) {
    return "—";
  }
  return meters < 1000 ? `${Math.round(meters)} m` : `${(meters / 1000).toFixed(1)} km`;
}
