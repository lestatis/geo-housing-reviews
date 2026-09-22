import { nativePluralRules } from "./intl";
import type { Locale } from "./locale";

/**
 * Plural category selection.
 *
 * `Intl.PluralRules` is the source of truth when the runtime provides it; the fallback below covers
 * English and Russian with the same categories, so the message catalogue does not have to care
 * which path produced them. The fallback exists because a React Native runtime can ship without
 * full ICU data, and a device that cannot pluralize "5 отзывов" is a visible product defect, not a
 * cosmetic one.
 */

export type PluralCategory = "one" | "few" | "many" | "other";

export function selectPluralCategory(locale: Locale, count: number): PluralCategory {
  const value = Math.abs(count);
  const rules = nativePluralRules(locale);
  if (rules) {
    return rules.select(value) as PluralCategory;
  }
  return fallbackPluralCategory(locale, value);
}

/** The local en/ru implementation, kept exported so tests can pin both paths to the same answers. */
export function fallbackPluralCategory(locale: Locale, count: number): PluralCategory {
  const value = Math.abs(count);
  if (locale === "ru") {
    if (!Number.isInteger(value)) {
      return "other";
    }
    const mod10 = value % 10;
    const mod100 = value % 100;
    if (mod10 === 1 && mod100 !== 11) {
      return "one";
    }
    if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
      return "few";
    }
    return "many";
  }
  return value === 1 ? "one" : "other";
}
