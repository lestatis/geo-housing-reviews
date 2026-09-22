/**
 * English is the source of truth for the catalogue.
 *
 * `MessageDictionary` is derived from this object, so a Russian key this file does not define — or
 * a key this file defines twice under different names — is a compile error rather than a blank
 * label on a device (plan 023, "Localization").
 *
 * Every message is a whole phrase with named placeholders. Nothing is assembled by concatenating
 * fragments, because Russian and English do not agree on word order, and none of these strings may
 * expose a URL, a status code or a raw server message.
 */
export const en = {
  "app.name": "Geo Housing Reviews",
  "home.tagline": "Find a building. Read what living there is like.",
  "home.languageHeading": "Language",
  "home.languageEnglish": "English",
  "home.languageRussian": "Русский",
  "home.languageSwitchLabel": "Switch the interface language to {language}",
  "home.diagnosticsHeading": "Localization check",
  "home.diagnosticsLocaleLine": "Active locale: {locale}",
  "home.diagnosticsPluralLine": "Review count forms: {forms}",
  "home.diagnosticsDateLine": "Date formatting: {date}",
  "home.diagnosticsIntlLine": "Intl support: {support}",
  "home.diagnosticsIntlNative": "native",
  "home.diagnosticsIntlFallback": "local fallback",
  "home.diagnosticsNote":
    "This screen proves the app boots: routing, localization and styling. Search and property reviews arrive in later milestones.",
} as const;

export const enPlurals = {
  "reviews.count": { one: "{count} review", other: "{count} reviews" },
} as const;

export type MessageKey = keyof typeof en;

export type MessageDictionary = Record<MessageKey, string>;

export type PluralKey = keyof typeof enPlurals;

export type PluralForms = { one: string; other: string; few?: string; many?: string };

export type PluralDictionary = Record<PluralKey, PluralForms>;

/**
 * Russian needs three plural forms — "1 отзыв", "2 отзыва", "5 отзывов" — so the Russian catalogue
 * is held to all four `Intl.PluralRules` categories. English declares `one` and `other` and reads
 * the others through its `other` form.
 */
export type CompletePluralDictionary = Record<
  PluralKey,
  { one: string; few: string; many: string; other: string }
>;
