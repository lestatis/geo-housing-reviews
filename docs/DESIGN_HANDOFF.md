# Design Handoff for Figma

Документ связывает продуктовую логику с интерфейсом. Визуальный стиль определяет Даша; здесь зафиксированы обязательные UX-состояния.

## Product tone

- trustworthy, calm, local, transparent;
- не «элитная недвижимость» и не доска объявлений;
- критические отзывы не должны выглядеть как сенсационный контент;
- badges должны объяснять сигнал, а не создавать ложное ощущение государственной проверки;
- карта и данные важнее декоративных блоков.

## Primary navigation

1. Search/Map
2. Saved/Compare — можно отложить
3. Add review
4. Notifications
5. Profile

## MVP screens

### Search and map

- строка поиска;
- переключение list/map;
- фильтры города/района/типа;
- состояние «ничего не найдено» с добавлением объекта;
- duplicate suggestions при добавлении.

### Property card

- название и aliases;
- адрес/район;
- количество отзывов;
- summary с уровнем достаточности данных;
- category chips;
- verified-review count;
- предупреждение, если данных мало.

### Property detail

- overview;
- category ratings;
- review feed;
- filters and sort explanation;
- photos;
- replies and issue resolution;
- report incorrect property information.

### Review composer

Разбить на короткие шаги:

1. relationship and period;
2. category ratings;
3. pros/cons/text;
4. photos;
5. verification choice;
6. privacy preview;
7. submit.

Показывать auto-save, прогресс, character guidance и конкретные предупреждения о персональных данных.

### Verification

Для каждого метода показать:

- что подтверждается;
- что будет загружено;
- кто увидит;
- срок хранения;
- когда badge истекает;
- возможность продолжить без верификации.

### Moderation status

Нужны состояния:

- checking;
- needs changes;
- rejected with reason;
- published;
- disputed;
- hidden temporarily;
- appeal submitted.

### Admin

Desktop-first:

- queues with risk filters;
- side-by-side content/evidence, with sensitive fields masked;
- decision reason required;
- previous decisions and audit trail;
- no accidental one-click destructive actions.

## Components and states

- verification badge + tooltip/sheet;
- data sufficiency indicator;
- rating distribution;
- review version marker;
- representative reply;
- moderation notice;
- sensitive-content redaction;
- report dialog;
- empty/loading/error/offline states;
- translation state with original-language toggle;
- deleted/merged property redirect.

## Accessibility

- badges cannot rely on color alone;
- rating controls need labels and keyboard/screen-reader equivalents;
- minimum touch targets;
- readable contrast;
- text expansion for Georgian/Russian/English;
- do not embed essential text in images.

## Design deliverables before implementation

- user flow map;
- low-fidelity wireframes;
- component inventory;
- design tokens;
- high-fidelity screens for primary flows;
- prototype for review submission and verification;
- copy deck in at least the initial launch language;
- error and moderation states, not only happy path.
