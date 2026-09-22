/**
 * Removes one `Intl` capability for the duration of a test.
 *
 * The fallbacks in `plural.ts` and `format.ts` exist for runtimes that ship without full ICU data,
 * which cannot be reproduced by configuring this one. The property is restored in a `finally` so a
 * failing assertion cannot leak the stub into the rest of the suite.
 */
export async function withoutIntl(
  capability: "PluralRules" | "DateTimeFormat",
  body: () => void | Promise<void>,
): Promise<void> {
  const original = Intl[capability];
  Object.defineProperty(Intl, capability, { value: undefined, configurable: true, writable: true });
  try {
    await body();
  } finally {
    Object.defineProperty(Intl, capability, {
      value: original,
      configurable: true,
      writable: true,
    });
  }
}
