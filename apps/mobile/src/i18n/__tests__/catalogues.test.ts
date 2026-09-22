import { en, enPlurals, type MessageKey, type PluralKey } from "../en";
import { ru, ruPlurals } from "../ru";

/**
 * Type-level completeness is enforced by `tsc` (both catalogues are typed against the English one).
 * These runtime checks are the backstop a reviewer can read: they fail loudly if somebody loosens
 * that typing to `Record<string, string>` later.
 */

function placeholders(message: string): string[] {
  return [...message.matchAll(/\{(\w+)\}/g)].map((match) => match[1] ?? "").sort();
}

describe("message catalogues", () => {
  it("defines the same keys in English and Russian", () => {
    expect(Object.keys(ru).sort()).toEqual(Object.keys(en).sort());
  });

  it("uses the same named placeholders in both locales", () => {
    for (const key of Object.keys(en) as MessageKey[]) {
      expect({ key, placeholders: placeholders(ru[key]) }).toEqual({
        key,
        placeholders: placeholders(en[key]),
      });
    }
  });

  it("never leaves a placeholder unresolved by the locale dictionaries", () => {
    for (const key of Object.keys(en) as MessageKey[]) {
      expect(`${key}:${en[key]}`).not.toMatch(/undefined/);
      expect(`${key}:${ru[key]}`).not.toMatch(/undefined/);
    }
  });

  it("gives Russian the three forms its grammar needs and English one and other", () => {
    expect(Object.keys(ruPlurals).sort()).toEqual(Object.keys(enPlurals).sort());
    for (const key of Object.keys(enPlurals) as PluralKey[]) {
      expect(Object.keys(ruPlurals[key]).sort()).toEqual(["few", "many", "one", "other"]);
    }
  });
});
