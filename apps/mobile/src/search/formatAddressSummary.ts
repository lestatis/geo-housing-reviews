export interface AddressSummaryParts {
  city?: string | null;
  district?: string | null;
  street?: string | null;
  building?: string | null;
}

function present(value: string | null | undefined): string {
  return value?.trim() ?? "";
}

/**
 * Formats the public address summary without exposing free text or inventing placeholders.
 *
 * The order is the plan's "street building, district, city". Blank parts are omitted so a partial
 * address still reads as a phrase, and a missing address row renders nothing rather than "undefined".
 */
export function formatAddressSummary(
  address: AddressSummaryParts | null | undefined,
): string {
  if (!address) {
    return "";
  }
  const streetAndBuilding = [present(address.street), present(address.building)]
    .filter((part) => part.length > 0)
    .join(" ");
  return [streetAndBuilding, present(address.district), present(address.city)]
    .filter((part) => part.length > 0)
    .join(", ");
}
