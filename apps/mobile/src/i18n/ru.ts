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
  "home.searchLabel": "Поиск здания",
  "home.searchPlaceholder": "Название здания, комплекса или улицы",
  "home.searchAction": "Найти",
  "results.heading": "Результаты по запросу «{query}»",
  "results.searching": "Идёт поиск",
  "results.emptyTitle": "По запросу «{query}» ничего не найдено",
  "results.emptyHint":
    "Проверьте написание, попробуйте указать улицу вместо здания или выбрать район.",
  "results.offlineError": "Нет подключения. Проверьте интернет и попробуйте снова.",
  "results.temporaryError": "Поиск временно недоступен. Попробуйте снова.",
  "results.retry": "Повторить",
  "results.firstPageNotice": "Показаны первые {limit} результатов.",
  "results.missingQuery": "Введите название здания для поиска.",
  "results.rowAccessibilityLabel": "{name}, {address}, {type}. Открывает подробности.",
  "results.rowAccessibilityLabelNoAddress": "{name}, {type}. Открывает подробности.",
  "propertyType.building": "Здание",
  "propertyType.residentialComplex": "Жилой комплекс",
  "propertyType.block": "Блок",
  "propertyType.phase": "Очередь",
  "propertyType.unknown": "Объект",
  "propertyPlaceholder.title": "Подробности об объекте появятся позже",
  "propertyPlaceholder.back": "Назад",
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
