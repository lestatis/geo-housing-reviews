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
  "home.searchLabel": "Search the catalogue",
  "home.searchPlaceholder": "Building, complex or street",
  "home.searchButton": "Search",
  "home.languageHeading": "Language",
  "home.languageEnglish": "English",
  "home.languageRussian": "Русский",
  "home.languageSwitchLabel": "Switch the interface language to {language}",
  "search.title": "Search",
  "search.back": "Back",
  "search.heading": "Results for «{query}»",
  "search.loading": "Searching",
  "search.emptyTitle": "No building found for «{query}»",
  "search.emptyHint":
    "Try another spelling, the street instead of the building, or the district.",
  "search.errorOffline": "No connection. Check your connection and try again.",
  "search.errorTemporary": "This is temporarily unavailable. Please try again.",
  "search.retry": "Retry",
  "search.showingFirst": "Showing the first {count}",
  "search.noAddress": "Address not recorded",
  "search.unknownType": "Type not recorded",
  "search.rowLabel": "{name}, {address}, {type}",
  "propertyType.building": "Building",
  "propertyType.residentialComplex": "Residential complex",
  "propertyType.block": "Block",
  "propertyType.phase": "Phase",
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
