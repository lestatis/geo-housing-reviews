import type { components } from "../api/generated/schema";
import type { PropertySearchResponse } from "../api/search";
import type { MessageKey } from "../i18n/en";

/**
 * Turns a search response into what a result row renders.
 *
 * This is the whole of the data mapping plan 023 allows: name, one address line and a translated
 * type. `score` and `distanceMeters` are read by nobody, so a ranking score can never leak into the
 * UI by accident — the row simply has no field for it.
 */

/**
 * The address summary as it arrives on the wire: the backend sends JSON `null` for an unrecorded
 * part, which the generated type does not express (see the search guard).
 */
export type AddressSummary = {
  [K in keyof components["schemas"]["AddressSummaryView"]]?: string | null;
};

export interface PropertySearchRow {
  propertyId: string;
  name: string;
  /** "street building, district, city" with blanks omitted; null when nothing is recorded. */
  address: string | null;
  /** The raw contract value; the screen translates it and never prints it as-is. */
  type: string | null;
}

const TYPE_KEYS: Record<string, MessageKey> = {
  BUILDING: "propertyType.building",
  RESIDENTIAL_COMPLEX: "propertyType.residentialComplex",
  BLOCK: "propertyType.block",
  PHASE: "propertyType.phase",
};

export function propertyTypeKey(type: string | null): MessageKey | undefined {
  return type === null ? undefined : TYPE_KEYS[type];
}

export function toSearchRows(response: PropertySearchResponse): PropertySearchRow[] {
  return (response.items ?? []).map((hit) => ({
    propertyId: hit.propertyId ?? "",
    name: hit.canonicalName ?? "",
    address: formatAddressSummary(hit.address),
    type: hit.type ?? null,
  }));
}

/**
 * "street building, district, city" — the order someone reads an address aloud in Batumi — with
 * blank parts omitted rather than leaving stray commas.
 */
export function formatAddressSummary(address: AddressSummary | null | undefined): string | null {
  if (!address) {
    return null;
  }
  const streetLine =
    [address.street, address.building].map(trimmed).filter(isPresent).join(" ") || undefined;
  const parts = [streetLine, trimmed(address.district), trimmed(address.city)].filter(isPresent);
  return parts.length === 0 ? null : parts.join(", ");
}

function trimmed(value: string | null | undefined): string | undefined {
  const text = value?.trim();
  return text === "" ? undefined : text;
}

function isPresent(value: string | undefined): value is string {
  return value !== undefined;
}
