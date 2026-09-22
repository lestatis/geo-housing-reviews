import AsyncStorage from "@react-native-async-storage/async-storage";

import { matchLocaleTag, type Locale } from "./locale";

/**
 * Where a manual language choice is kept.
 *
 * There is no account and no server preference in this slice, so the override lives on the device
 * and nowhere else. The interface is what the provider depends on, so tests drive locale switching
 * without a native store.
 */
export interface LocalePreferenceStore {
  load(): Promise<Locale | undefined>;
  save(locale: Locale): Promise<void>;
}

const LOCALE_KEY = "geo-housing.locale";

/**
 * The device store. A failure to read or write a language preference is not worth failing a launch
 * over, so it degrades to "no stored preference" and the device locale decides.
 */
export const persistentLocaleStore: LocalePreferenceStore = {
  async load(): Promise<Locale | undefined> {
    try {
      const stored = await AsyncStorage.getItem(LOCALE_KEY);
      return matchLocaleTag(stored);
    } catch {
      // Storage unavailable; the device locale is still a valid answer.
      return undefined;
    }
  },
  async save(locale: Locale): Promise<void> {
    try {
      await AsyncStorage.setItem(LOCALE_KEY, locale);
    } catch {
      // Losing the override is survivable; the app must not crash on a storage failure.
    }
  },
};

/** An in-memory store, used by tests in place of the device store. */
export function createMemoryLocaleStore(initial?: Locale): LocalePreferenceStore {
  let value = initial;
  return {
    async load() {
      return value;
    },
    async save(locale: Locale) {
      value = locale;
    },
  };
}
