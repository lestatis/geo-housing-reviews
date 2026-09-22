import { messagesFor, pluralsFor } from "./catalogues";
import type { MessageKey, PluralKey } from "./en";
import { nativeDateTimeFormat } from "./intl";
import type { Locale } from "./locale";
import { selectPluralCategory } from "./plural";

/** Message parameters. Named placeholders only — a positional argument is impossible to reorder. */
export type MessageParams = Record<string, string | number>;

/**
 * Replaces `{name}` placeholders. A placeholder with no parameter is left visible rather than
 * silently dropped, so a missing argument shows up in review instead of as a half-sentence.
 */
export function interpolate(template: string, params?: MessageParams): string {
  if (!params) {
    return template;
  }
  return template.replace(/\{(\w+)\}/g, (match, name: string) =>
    Object.prototype.hasOwnProperty.call(params, name) ? String(params[name]) : match,
  );
}

export function translate(locale: Locale, key: MessageKey, params?: MessageParams): string {
  return interpolate(messagesFor(locale)[key], params);
}

/**
 * Formats a count with the right plural form for the locale. Values are interpolated as digits,
 * without locale digit grouping: a count in a sentence is a number, not a formatted quantity.
 */
export function formatCount(locale: Locale, key: PluralKey, count: number): string {
  const forms = pluralsFor(locale)[key];
  const category = selectPluralCategory(locale, count);
  const template = forms[category] ?? forms.other;
  return interpolate(template, { count });
}

export type DateFormatName = "dayMonthYear";

const DATE_FORMAT_OPTIONS: Record<DateFormatName, Intl.DateTimeFormatOptions> = {
  dayMonthYear: { day: "numeric", month: "long", year: "numeric" },
};

export function formatDate(
  locale: Locale,
  value: Date | number,
  format: DateFormatName = "dayMonthYear",
): string {
  const date = value instanceof Date ? value : new Date(value);
  const options = DATE_FORMAT_OPTIONS[format];
  const formatter = nativeDateTimeFormat(locale, options);
  if (formatter) {
    return formatter.format(date);
  }
  return fallbackFormatDate(locale, date);
}

const EN_MONTHS = [
  "January",
  "February",
  "March",
  "April",
  "May",
  "June",
  "July",
  "August",
  "September",
  "October",
  "November",
  "December",
];

/** Genitive, because a Russian date reads "1 сентября 2026 г.", not "1 сентябрь 2026 г.". */
const RU_MONTHS_GENITIVE = [
  "января",
  "февраля",
  "марта",
  "апреля",
  "мая",
  "июня",
  "июля",
  "августа",
  "сентября",
  "октября",
  "ноября",
  "декабря",
];

/**
 * The `en`/`ru` date formatting used when the runtime has no `Intl.DateTimeFormat`. It covers the
 * one format this slice needs; it is not a date library.
 */
export function fallbackFormatDate(locale: Locale, date: Date): string {
  const day = date.getDate();
  const month = date.getMonth();
  const year = date.getFullYear();
  if (locale === "ru") {
    return `${day} ${RU_MONTHS_GENITIVE[month]} ${year} г.`;
  }
  return `${EN_MONTHS[month]} ${day}, ${year}`;
}
