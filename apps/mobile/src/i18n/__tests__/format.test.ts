import { fallbackFormatDate, formatDate, interpolate, translate } from "../format";
import { withoutIntl } from "./intlStub";

describe("message interpolation", () => {
  it("substitutes named placeholders", () => {
    expect(interpolate("Active locale: {locale}", { locale: "ru" })).toBe("Active locale: ru");
  });

  it("leaves an unfilled placeholder visible instead of dropping it", () => {
    expect(interpolate("Date: {date}")).toBe("Date: {date}");
  });

  it("translates whole phrases rather than assembled fragments", () => {
    expect(translate("ru", "home.diagnosticsLocaleLine", { locale: "ru" })).toBe(
      "Активный язык: ru",
    );
    expect(translate("en", "home.diagnosticsLocaleLine", { locale: "en" })).toBe(
      "Active locale: en",
    );
  });
});

describe("date formatting", () => {
  // Local midnight, so the rendered day does not depend on the reader's time zone.
  const firstOfSeptember = new Date(2026, 8, 1);

  it("formats a Russian date through the runtime's Intl data", () => {
    expect(formatDate("ru", firstOfSeptember)).toMatch(/^1 сентября 2026/);
  });

  it("formats an English date through the runtime's Intl data", () => {
    expect(formatDate("en", firstOfSeptember)).toMatch(/^September 1, 2026/);
  });

  it("falls back to the local en/ru implementation when Intl.DateTimeFormat is missing", async () => {
    await withoutIntl("DateTimeFormat", () => {
      expect(formatDate("ru", firstOfSeptember)).toBe("1 сентября 2026 г.");
      expect(formatDate("en", firstOfSeptember)).toBe("September 1, 2026");
    });
  });

  it("uses the Russian genitive month even without Intl", () => {
    expect(fallbackFormatDate("ru", new Date(2026, 0, 31))).toBe("31 января 2026 г.");
  });

  it("keeps the fallback identical to the runtime's formatting for both locales", () => {
    // The Android device reported the fallback, so the fallback is what users actually see. This is
    // the date equivalent of pinning the plural fallback to Intl.PluralRules: if CLDR changes shape
    // or the fallback drifts, one of these fails instead of a silent wording difference.
    for (const locale of ["en", "ru"] as const) {
      expect({ locale, rendered: formatDate(locale, firstOfSeptember) }).toEqual({
        locale,
        rendered: fallbackFormatDate(locale, firstOfSeptember),
      });
      expect({ locale, rendered: formatDate(locale, new Date(2026, 0, 31)) }).toEqual({
        locale,
        rendered: fallbackFormatDate(locale, new Date(2026, 0, 31)),
      });
    }
  });
});
