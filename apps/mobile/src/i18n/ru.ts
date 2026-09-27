import type { CompletePluralDictionary, MessageDictionary } from "./en";

/**
 * The Russian catalogue. Typed against the English source of truth, so a missing key is a compile
 * error and an unknown key is a compile error rather than a string nobody ever sees.
 */
export const ru: MessageDictionary = {
  "app.name": "Geo Housing Reviews",
  "home.tagline": "Найдите дом. Узнайте, каково там жить.",
  "home.searchLabel": "Поиск по каталогу",
  "home.searchPlaceholder": "Дом, комплекс или улица",
  "home.searchButton": "Найти",
  "home.languageHeading": "Язык",
  "home.languageEnglish": "English",
  "home.languageRussian": "Русский",
  "home.languageSwitchLabel": "Переключить язык интерфейса на {language}",
  "search.title": "Поиск",
  "search.back": "Назад",
  "search.heading": "Результаты по запросу «{query}»",
  "search.loading": "Идёт поиск",
  "search.emptyTitle": "Ничего не найдено по запросу «{query}»",
  "search.emptyHint": "Проверьте написание, попробуйте улицу вместо дома или район.",
  "search.errorOffline": "Нет подключения. Проверьте сеть и попробуйте снова.",
  "search.errorTemporary": "Временно недоступно. Попробуйте ещё раз.",
  "search.retry": "Повторить",
  "search.showingFirst": "Показаны первые {count}",
  "search.noAddress": "Адрес не указан",
  "search.unknownType": "Тип не указан",
  "search.rowLabel": "{name}, {address}, {type}",
  "propertyType.building": "Дом",
  "propertyType.residentialComplex": "Жилой комплекс",
  "propertyType.block": "Корпус",
  "propertyType.phase": "Очередь",
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
