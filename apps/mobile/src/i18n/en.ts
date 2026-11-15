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
  "home.searchLabel": "Search for a building",
  "home.searchPlaceholder": "Building, complex or street name",
  "home.searchAction": "Search",
  "results.heading": "Results for «{query}»",
  "results.searching": "Searching",
  "results.emptyTitle": "No building found for «{query}»",
  "results.emptyHint":
    "Check the spelling, try the street instead of the building, or try the district.",
  "results.offlineError": "No connection. Check your internet connection and try again.",
  "results.temporaryError": "Search is temporarily unavailable. Please try again.",
  "results.retry": "Retry",
  "results.firstPageNotice": "Showing the first {limit} results.",
  "results.missingQuery": "Enter a building name to search.",
  "results.rowAccessibilityLabel": "{name}, {address}, {type}. Opens details.",
  "results.rowAccessibilityLabelNoAddress": "{name}, {type}. Opens details.",
  "propertyType.building": "Building",
  "propertyType.residentialComplex": "Residential complex",
  "propertyType.block": "Block",
  "propertyType.phase": "Phase",
  "propertyType.unknown": "Property",
  "propertyPlaceholder.title": "Property details are coming next",
  "propertyPlaceholder.back": "Back",
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
