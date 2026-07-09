# PRD: MVP отзывов о жилье в Грузии

Status: Draft for founder review

## 1. Problem

Информация о жилье распределена между объявлениями, чатами и случайными комментариями. Она плохо структурирована, быстро теряется, часто не привязана к точному объекту и редко содержит проверяемый контекст проживания.

## 2. MVP objective

Позволить пользователю найти дом/ЖК, изучить структурированный опыт жильцов и оставить собственный отзыв с понятным уровнем подтверждения.

## 3. Core personas

### Seeker

Ищет аренду или покупку. Нужны поиск, сравнение, карта, категории проблем, свежесть отзывов и доверительные сигналы.

### Reviewer

Текущий или бывший жилец/собственник. Нужны безопасная публикация, понятные вопросы, возможность обновить отзыв и подтвердить связь с объектом.

### Moderator

Рассматривает новые отзывы, жалобы, доказательства и споры. Нужны очередь, контекст, история решений и ограничения доступа.

### Property representative — limited MVP

Застройщик, управляющая компания или подтверждённый представитель. Может дать публичный ответ, но не удалять отзыв и не влиять на рейтинг.

## 4. Main user flows

### 4.1 Find a property

1. Ввести название, адрес или выбрать точку на карте.
2. Увидеть варианты с нормализованным адресом.
3. Открыть карточку.
4. Просмотреть общий summary, категории и отзывы.

### 4.2 Add a missing property

1. Пользователь не находит объект.
2. Указывает адрес/точку, тип объекта и известное название.
3. Система проверяет потенциальные дубликаты.
4. Объект попадает в очередь подтверждения/объединения.

### 4.3 Submit a review

1. Выбрать роль: арендатор, бывший арендатор, собственник, бывший собственник, гость/иная роль.
2. Указать период проживания с допустимой точностью по месяцам.
3. Оценить обязательные и необязательные категории.
4. Написать плюсы, минусы и итог.
5. Добавить фотографии.
6. Выбрать способ подтверждения либо опубликовать неподтверждённый отзыв.
7. Пройти автоматические проверки и модерацию.

### 4.4 Verify experience

1. Пользователь выбирает доступный метод.
2. Получает объяснение, какие данные нужны и когда они будут удалены.
3. Отправляет доказательство или проходит слабую проверку.
4. Система присваивает тип и срок действия сигнала.
5. Публично показывается только безопасный badge, не документ.

### 4.5 Report and dispute

1. Пользователь жалуется на конкретную причину.
2. Модератор получает контекст и историю.
3. Автор может отредактировать, предоставить контекст или обжаловать.
4. Решение и основание фиксируются в audit trail.

## 5. Functional requirements

### 5.1 Property catalogue

- тип: building, residential complex, phase/block;
- каноническое название и алиасы на нескольких языках;
- нормализованный адрес;
- координаты;
- город, район, микрорайон;
- связь ЖК → корпус/дом;
- статус: draft, active, merged, hidden;
- источники данных и история объединений.

### 5.2 Property page

- название, адрес, карта и фото;
- количество отзывов и дата последнего;
- общий score с объяснением достаточности данных;
- scores по категориям;
- распределение оценок;
- фильтр: verified only, current/former resident, период, язык;
- сортировки: recommended, newest, most helpful;
- видимое объяснение, почему отзыв выше другого;
- ответы представителей и статус решения проблемы.

### 5.3 Review schema

Обязательное:

- property;
- relationship role;
- residence period;
- overall recommendation;
- минимум один плюс и один минус либо объяснение отсутствия;
- подтверждение правил публикации.

Категории MVP:

- sound insulation/noise;
- utilities and outages;
- heating/temperature;
- elevators and common areas;
- cleanliness/pests/mould;
- management/maintenance;
- safety/access;
- neighbourhood/transport;
- construction quality;
- value for money.

Категории должны поддерживать `not_applicable` и «не могу оценить».

### 5.4 Review lifecycle

`draft → submitted → automated_checks → moderation → published`

Дополнительные состояния:

- needs_changes;
- rejected;
- hidden_pending_dispute;
- removed;
- superseded_by_update.

Изменение опубликованного отзыва создаёт версию. Существенная правка повторно проходит проверки.

### 5.5 Verification

Поддержать модель нескольких методов и уровней, не один boolean. Детали: `docs/TRUST_VERIFICATION.md`.

### 5.6 Moderation

- автоматические признаки риска;
- ручная очередь;
- причины решения;
- redaction вместо удаления, когда достаточно скрыть персональные данные;
- апелляция;
- право на ответ;
- аудит действий.

### 5.7 Account

- email/social login для MVP;
- подтверждение email;
- псевдоним публично;
- приватная роль/история верификации;
- список отзывов, черновиков, жалоб и апелляций;
- экспорт и удаление данных с учётом законных оснований хранения.

### 5.8 Admin

- property merge queue;
- review moderation queue;
- verification queue;
- reports/disputes;
- user restrictions;
- audit log;
- basic metrics;
- role-based access.

## 6. Ranking baseline

Recommended sorting should combine:

- verification strength;
- review completeness;
- recency with reasonable decay;
- helpfulness with abuse resistance;
- diversity so one campaign/user cohort does not dominate;
- moderation confidence;
- property-specific relevance.

Hard rules:

- paid status must not increase organic review rank;
- verification is a boost, not a guarantee of first position forever;
- newest critical information must remain discoverable;
- ranking version must be stored for audit/experiments;
- never expose a manipulable exact formula publicly.

## 7. Non-functional requirements

- mobile-first;
- multilingual data model;
- WCAG-oriented accessible UI;
- p95 read latency target established after first load test;
- object-level authorization tests;
- append-only audit events for sensitive actions;
- backups and restore test before public launch;
- observability without logging sensitive payloads;
- deletion/retention jobs idempotent and auditable.

## 8. MVP acceptance

MVP is launchable when a user can find/add a property, submit an unverified or verified review, see transparent ranking signals, report content, and receive a moderated outcome, while administrators can manage duplicates, evidence and disputes without direct database editing.

## 9. Open founder decisions

Tracked in `docs/DECISION_LOG.md`:

- whether exact building address is public for all object types;
- legal entity and moderation jurisdiction (P-010).

First launch city, public languages, authentication provider, initial verification methods, pre-moderation vs post-moderation, and anonymous public profile policy were accepted — see `docs/DECISION_LOG.md` (P-001, P-002, P-003, P-004, P-005, P-008).
