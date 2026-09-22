import { formatCount } from "../format";
import { fallbackPluralCategory, selectPluralCategory } from "../plural";
import { withoutIntl } from "./intlStub";

describe("Russian plural handling", () => {
  it.each([
    [0, "0 отзывов"],
    [1, "1 отзыв"],
    [2, "2 отзыва"],
    [5, "5 отзывов"],
    [11, "11 отзывов"],
    [21, "21 отзыв"],
    [22, "22 отзыва"],
    [25, "25 отзывов"],
    [101, "101 отзыв"],
    [111, "111 отзывов"],
  ])("renders %i as «%s»", (count, expected) => {
    expect(formatCount("ru", "reviews.count", count)).toBe(expected);
  });

  it("renders English one and other", () => {
    expect(formatCount("en", "reviews.count", 1)).toBe("1 review");
    expect(formatCount("en", "reviews.count", 2)).toBe("2 reviews");
    expect(formatCount("en", "reviews.count", 0)).toBe("0 reviews");
  });

  it("agrees with Intl.PluralRules for every category the local fallback claims to cover", () => {
    // The fallback exists for runtimes without full ICU data; if it ever disagrees with the native
    // rules, a device would render different words than the tests do.
    const samples = [...Array.from({ length: 201 }, (_, index) => index), 1.5, 2.5, 11.5];
    for (const locale of ["en", "ru"] as const) {
      const rules = new Intl.PluralRules(locale);
      for (const value of samples) {
        expect({ locale, value, category: fallbackPluralCategory(locale, value) }).toEqual({
          locale,
          value,
          category: rules.select(value),
        });
      }
    }
  });

  it("still renders 1, 2 and 5 correctly when the runtime has no Intl.PluralRules", async () => {
    await withoutIntl("PluralRules", () => {
      expect(selectPluralCategory("ru", 1)).toBe("one");
      expect(selectPluralCategory("ru", 2)).toBe("few");
      expect(selectPluralCategory("ru", 5)).toBe("many");
      expect(formatCount("ru", "reviews.count", 1)).toBe("1 отзыв");
      expect(formatCount("ru", "reviews.count", 2)).toBe("2 отзыва");
      expect(formatCount("ru", "reviews.count", 5)).toBe("5 отзывов");
    });
  });
});
