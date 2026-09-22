import type { CompletePluralDictionary, MessageDictionary } from "./en";

/**
 * The Russian catalogue. Typed against the English source of truth, so a missing key is a compile
 * error and an unknown key is a compile error rather than a string nobody ever sees.
 */
export const ru: MessageDictionary = {
  "app.name": "Geo Housing Reviews",
  "home.tagline": "Найдите дом. Узнайте, каково там жить.",
  "home.languageHeading": "Язык",
  "home.languageEnglish": "English",
  "home.languageRussian": "Русский",
  "home.languageSwitchLabel": "Переключить язык интерфейса на {language}",
  "home.diagnosticsHeading": "Проверка локализации",
  "home.diagnosticsLocaleLine": "Активный язык: {locale}",
  "home.diagnosticsPluralLine": "Формы количества отзывов: {forms}",
  "home.diagnosticsDateLine": "Формат даты: {date}",
  "home.diagnosticsIntlLine": "Поддержка Intl: {support}",
  "home.diagnosticsIntlNative": "нативная",
  "home.diagnosticsIntlFallback": "локальный резерв",
  "home.diagnosticsNote":
    "Этот экран лишь доказывает, что приложение запускается: маршрутизация, локализация и оформление. Поиск и отзывы о домах появятся на следующих этапах.",
};

/**
 * `other` covers fractions and any category the runtime reports that Russian grammar does not
 * distinguish here ("1,5 отзыва"); `few` and `many` are the forms English does not have.
 */
export const ruPlurals: CompletePluralDictionary = {
  "reviews.count": {
    one: "{count} отзыв",
    few: "{count} отзыва",
    many: "{count} отзывов",
    other: "{count} отзыва",
  },
};
