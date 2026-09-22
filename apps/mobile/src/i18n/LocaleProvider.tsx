import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";

import type { MessageKey, PluralKey } from "./en";
import {
  formatCount,
  formatDate,
  translate,
  type DateFormatName,
  type MessageParams,
} from "./format";
import { resolveLocaleTag, type Locale } from "./locale";
import type { LocalePreferenceStore } from "./localeStore";

/** Everything a screen needs to render localized copy, bound to the active locale. */
export interface Messages {
  locale: Locale;
  setLocale(locale: Locale): void;
  t(key: MessageKey, params?: MessageParams): string;
  count(key: PluralKey, value: number): string;
  date(value: Date | number, format?: DateFormatName): string;
}

const LocaleContext = createContext<Messages | undefined>(undefined);

export interface LocaleProviderProps {
  store: LocalePreferenceStore;
  /** The device's preferred language tag, used until a manual override exists. */
  deviceLocale?: string | undefined;
  children: ReactNode;
}

/**
 * Holds the active locale.
 *
 * The initial choice is the device locale; a stored manual override replaces it once the device
 * store answers. The read is asynchronous, so a choice the user makes before it resolves wins —
 * otherwise a slow disk could silently undo the tap that just happened.
 */
export function LocaleProvider({ store, deviceLocale, children }: LocaleProviderProps) {
  const [locale, setLocaleState] = useState<Locale>(() => resolveLocaleTag(deviceLocale));
  const chosenByUser = useRef(false);

  useEffect(() => {
    let active = true;
    store
      .load()
      .then((stored) => {
        if (active && !chosenByUser.current && stored) {
          setLocaleState(stored);
        }
      })
      .catch(() => {
        // A store that cannot be read leaves the device locale in place.
      });
    return () => {
      active = false;
    };
  }, [store]);

  const setLocale = useCallback(
    (next: Locale) => {
      chosenByUser.current = true;
      setLocaleState(next);
      // Persisting is best-effort: a failed write must not undo the choice just made.
      store.save(next).catch(() => {});
    },
    [store],
  );

  const messages = useMemo<Messages>(
    () => ({
      locale,
      setLocale,
      t: (key, params) => translate(locale, key, params),
      count: (key, value) => formatCount(locale, key, value),
      date: (value, format) => formatDate(locale, value, format),
    }),
    [locale, setLocale],
  );

  return <LocaleContext.Provider value={messages}>{children}</LocaleContext.Provider>;
}

export function useMessages(): Messages {
  const messages = useContext(LocaleContext);
  if (!messages) {
    throw new Error("useMessages must be used inside LocaleProvider");
  }
  return messages;
}
