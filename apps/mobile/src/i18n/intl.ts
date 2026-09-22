import type { Locale } from "./locale";

/**
 * Native `Intl` access, with the smallest honest fallback when a runtime lacks it.
 *
 * Plan 023 asks for exactly this check in 023-A, because discovering it later means rewriting every
 * count string. Every helper returns `undefined` rather than throwing, and the caller falls back to
 * the local implementation in `plural.ts` / `format.ts`. Nothing here turns ICU support into a
 * project.
 */

export type IntlSupport = "native" | "fallback";

export function intlSupport(): IntlSupport {
  const native =
    typeof Intl !== "undefined" &&
    typeof Intl.PluralRules === "function" &&
    typeof Intl.DateTimeFormat === "function";
  return native ? "native" : "fallback";
}

export function nativePluralRules(locale: Locale): Intl.PluralRules | undefined {
  if (typeof Intl === "undefined" || typeof Intl.PluralRules !== "function") {
    return undefined;
  }
  try {
    return new Intl.PluralRules(locale);
  } catch {
    // The runtime reports `Intl` but has no data for this locale; fall back rather than crash.
    return undefined;
  }
}

export function nativeDateTimeFormat(
  locale: Locale,
  options: Intl.DateTimeFormatOptions,
): Intl.DateTimeFormat | undefined {
  if (typeof Intl === "undefined" || typeof Intl.DateTimeFormat !== "function") {
    return undefined;
  }
  try {
    return new Intl.DateTimeFormat(locale, options);
  } catch {
    return undefined;
  }
}
