/**
 * The locales the app ships.
 *
 * English and Russian only, matching P-002 and plan 023's localization section. Georgian is a later
 * product question, not a gap to fill silently.
 */

export const SUPPORTED_LOCALES = ["en", "ru"] as const;

export type Locale = (typeof SUPPORTED_LOCALES)[number];

export const DEFAULT_LOCALE: Locale = "en";

/** The primary subtag of a BCP-47 tag, when it names a locale we ship. */
export function matchLocaleTag(tag: string | null | undefined): Locale | undefined {
  if (!tag) {
    return undefined;
  }
  const primary = tag.toLowerCase().split(/[-_]/)[0];
  return SUPPORTED_LOCALES.find((locale) => locale === primary);
}

/**
 * The locale to use for a device or stored tag, falling back to English.
 *
 * This is the whole of "use the device locale as the initial choice": `en-GB` and `ru-RU` resolve
 * to the two catalogues we actually have, and anything else (`ka-GE`, an empty tag, a tag we do not
 * recognize) lands on English rather than on a locale with no strings.
 */
export function resolveLocaleTag(tag: string | null | undefined): Locale {
  return matchLocaleTag(tag) ?? DEFAULT_LOCALE;
}
