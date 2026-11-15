import type { components } from "./generated/schema";
import { hasString, isRecord, type RuntimeGuard } from "./guards";

type GeneratedProperty = components["schemas"]["PropertyResponse"];
type GeneratedAddress = components["schemas"]["AddressView"];
type GeneratedAlias = components["schemas"]["AliasView"];

/**
 * An alias with the key the screen reads made required. The generated nullable fields are kept as
 * they are; a guard narrows what it proved, it never redefines the contract.
 */
export type PropertyAlias = Omit<GeneratedAlias, "name"> &
  Required<Pick<GeneratedAlias, "name">>;

/**
 * A conforming property with every field the detail header reads proven present. `address` stays
 * `AddressView | null` from the generated schema, and the deliberately removed `originalText` is
 * neither read nor required.
 */
export type Property = Omit<
  GeneratedProperty,
  "propertyId" | "canonicalName" | "type" | "status" | "address" | "aliases"
> & {
  propertyId: string;
  canonicalName: string;
  type: string;
  status: string;
  address: GeneratedAddress | null;
  aliases: PropertyAlias[];
};

const ADDRESS_PART_KEYS = ["city", "district", "street", "building"] as const;

function hasNullableString(record: Record<string, unknown>, key: string): boolean {
  return (
    Object.prototype.hasOwnProperty.call(record, key) &&
    (record[key] === null || typeof record[key] === "string")
  );
}

function isAddress(value: unknown): value is GeneratedAddress {
  return isRecord(value) && ADDRESS_PART_KEYS.every((key) => hasNullableString(value, key));
}

function isAlias(value: unknown): value is PropertyAlias {
  if (!isRecord(value) || !hasString(value, "name")) {
    return false;
  }
  return (
    !Object.prototype.hasOwnProperty.call(value, "locale") ||
    value.locale === null ||
    typeof value.locale === "string"
  );
}

/**
 * Checks the fields the property screen actually reads.
 *
 * Extra keys are ignored on purpose: a public body that still carried `authorAccountId` or a
 * removed address field must not make the screen fail, and the app must never read them.
 */
export const isPropertyResponse: RuntimeGuard<Property> = (value): value is Property => {
  if (
    !isRecord(value) ||
    !hasString(value, "propertyId") ||
    !hasString(value, "canonicalName") ||
    !hasString(value, "type") ||
    !hasString(value, "status") ||
    !Object.prototype.hasOwnProperty.call(value, "address") ||
    !Array.isArray(value.aliases)
  ) {
    return false;
  }
  return (
    (value.address === null || isAddress(value.address)) && value.aliases.every(isAlias)
  );
};
