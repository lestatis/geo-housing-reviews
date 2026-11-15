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
  "property.back": "Back",
  "property.loading": "Loading the building",
  "property.notFoundTitle": "This building is no longer listed",
  "property.offlineError": "No connection. Check your internet connection and try again.",
  "property.temporaryError": "This building is temporarily unavailable. Please try again.",
  "property.retry": "Retry",
  "property.aliases": "Also known as: {aliases}",
  "property.draftNotice":
    "This property was added by a user and has not yet been reviewed by our team.",
  "reviews.loading": "Loading reviews",
  "reviews.empty": "No published reviews yet.",
  "reviews.firstPageNotice": "Showing the first {limit} reviews.",
  "reviews.age.today": "Today",
  "reviews.age.yesterday": "Yesterday",
  "reviews.loadingMore": "Loading more reviews",
  "reviews.loadMore": "Load more reviews",
  "reviews.offlineError": "No connection. Check your internet connection and try again.",
  "reviews.temporaryError": "Reviews are temporarily unavailable. Please try again.",
  "reviews.retry": "Retry",
  "reviews.relationship.currentResident": "Current resident",
  "reviews.relationship.formerResident": "Former resident",
  "reviews.relationship.owner": "Owner",
  "reviews.relationship.other": "Other relationship",
  "reviews.relationship.unknown": "Relationship not specified",
  "reviews.residenceRange": "{from}–{to}",
  "reviews.residenceYear": "{year}",
  "reviews.residenceSince": "Since {year}",
  "reviews.residenceUntil": "Until {year}",
  "reviews.verification.unverified": "Relationship not verified",
  "reviews.verification.relationshipSignal": "Relationship signal confirmed",
  "reviews.verification.documentVerified": "Relationship checked: document",
  "reviews.verification.unknown": "Relationship verification unavailable",
  "reviews.verification.tooltip":
    "The platform checked evidence of the reviewer's relationship with this property. This does not certify every statement in the review.",
  "reviews.recommendation.recommend": "Recommends",
  "reviews.recommendation.neutral": "Neutral",
  "reviews.recommendation.notRecommend": "Does not recommend",
  "reviews.recommendation.unknown": "Recommendation unavailable",
  "reviews.rating.noise": "Noise",
  "reviews.rating.utilities": "Utilities",
  "reviews.rating.heating": "Heating",
  "reviews.rating.liftsCommonAreas": "Lifts and common areas",
  "reviews.rating.cleanliness": "Cleanliness",
  "reviews.rating.management": "Management",
  "reviews.rating.safety": "Safety",
  "reviews.rating.neighbourhood": "Neighbourhood",
  "reviews.rating.construction": "Construction quality",
  "reviews.rating.value": "Value for money",
  "reviews.rating.otherAspect": "Other aspect",
  "reviews.rating.withValue": "{category}: {value}/5",
  "reviews.rating.withoutValue": "{category}",
  "reviews.pros": "Pros",
  "reviews.cons": "Cons",
  "reviews.languageLabel": "Review language: {language}",
} as const;

export const enPlurals = {
  "reviews.count": { one: "{count} review", other: "{count} reviews" },
  "reviews.helpful": {
    one: "{count} person found this helpful",
    other: "{count} people found this helpful",
  },
  "reviews.age.days": { one: "{count} day ago", other: "{count} days ago" },
  "reviews.age.months": { one: "{count} month ago", other: "{count} months ago" },
  "reviews.age.years": { one: "{count} year ago", other: "{count} years ago" },
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
