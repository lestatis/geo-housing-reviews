import { DEFAULT_LOCALE, matchLocaleTag, resolveLocaleTag, SUPPORTED_LOCALES } from "../locale";

describe("locale tags", () => {
  it("ships exactly English and Russian, with English as the fallback", () => {
    expect([...SUPPORTED_LOCALES]).toEqual(["en", "ru"]);
    expect(DEFAULT_LOCALE).toBe("en");
  });

  it.each([
    ["en", "en"],
    ["en-US", "en"],
    ["en_GB", "en"],
    ["ru", "ru"],
    ["ru-RU", "ru"],
    ["RU-ru", "ru"],
  ])("maps %s to %s", (tag, expected) => {
    expect(resolveLocaleTag(tag)).toBe(expected);
  });

  it.each([
    ["ka-GE", "en"],
    ["de", "en"],
    ["", "en"],
    [undefined, "en"],
    [null, "en"],
  ])("falls back to English for %s", (tag, expected) => {
    expect(resolveLocaleTag(tag)).toBe(expected);
  });

  it("does not treat an unsupported tag as a stored preference", () => {
    // A stored "ka" must not win over the device locale: the app has no Georgian strings.
    expect(matchLocaleTag("ka")).toBeUndefined();
    expect(matchLocaleTag("ru-RU")).toBe("ru");
  });
});
