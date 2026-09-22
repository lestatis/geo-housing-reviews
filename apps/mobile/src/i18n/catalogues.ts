import type { Locale } from "./locale";
import { en, enPlurals, type MessageDictionary, type PluralDictionary } from "./en";
import { ru, ruPlurals } from "./ru";

/**
 * Both records are keyed by `Locale`, so adding a locale to `SUPPORTED_LOCALES` without shipping a
 * catalogue for it does not compile.
 */
const MESSAGES: Record<Locale, MessageDictionary> = { en, ru };

const PLURALS: Record<Locale, PluralDictionary> = { en: enPlurals, ru: ruPlurals };

export function messagesFor(locale: Locale): MessageDictionary {
  return MESSAGES[locale];
}

export function pluralsFor(locale: Locale): PluralDictionary {
  return PLURALS[locale];
}
