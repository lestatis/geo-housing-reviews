import { getLocales } from "expo-localization";

/**
 * The device's preferred language tag, if the platform will tell us.
 *
 * Only the initial choice comes from here; a manual override, once made, wins. This module is
 * imported by the root layout alone so that node-side tests never load a native module.
 */
export function deviceLocaleTag(): string | undefined {
  try {
    return getLocales()[0]?.languageTag;
  } catch {
    return undefined;
  }
}
