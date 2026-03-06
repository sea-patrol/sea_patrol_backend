# TASK-001 - Backend часть: канонический auth contract для MVP

## Метаданные
- **ID:** `TASK-001`
- **Тип:** `docs`
- **Статус:** `Review`
- **Приоритет:** `High`
- **Дата создания:** `2026-03-06`
- **Автор:** `Codex`
- **Связанный roadmap item:** `Wave 0 / M0 / TASK-001`
- **Трек:** `Backend`
- **Связанные внешние задачи:** `sea_patrol_orchestration/TASK-001`, `TASK-002`, `TASK-003`

## Контекст
`TASK-001` в roadmap является shared-задачей, но для backend репозитория нужна отдельная постановка только по зоне ответственности бэка. Здесь источник истины для auth contract задаётся backend кодом и backend tests, поэтому задача backend-репозитория состоит не в изменении frontend поведения, а в фиксации и документировании фактического backend contract.

На момент постановки задачи backend уже реализует такой auth flow:
- `POST /api/v1/auth/signup` -> `200 OK` + `{ username }`
- `POST /api/v1/auth/login` -> `{ username, token, issuedAt, expiresAt }`
- auth/security/validation errors -> `{ errors: [{ code, message }] }`

## Цель
Зафиксировать backend-часть канонического auth contract в документации backend-репозитория и явно обозначить, какие аспекты backend уже гарантирует для следующих задач `TASK-002` и `TASK-003`.

## Source of Truth
- Backend код / тесты:
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/auth/api/AuthController.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/auth/api/dto/AuthRequestDto.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/auth/api/dto/AuthResponseDto.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/auth/api/dto/UserRegistrationDto.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/error/api/AppErrorAttributes.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/error/api/ValidationErrorHandler.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/user/repository/InMemoryUserRepository.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/auth/AuthControllerTest.java`
- Внешние reference docs:
  - `sea_patrol_orchestration/API.md`
  - `sea_patrol_orchestration/ROADMAP.md`
  - `sea_patrol_orchestration/ROADMAP-TASKS.md`

## Acceptance Criteria
- [x] В backend docs явно зафиксировано текущее поведение `signup`, `login` и auth errors.
- [x] В backend docs больше нет ошибочного утверждения, что в auth нет `@Valid`.
- [x] В backend docs явно отмечено, что `userId` не входит в текущий login response contract.
- [x] В backend docs явно отмечено, что duplicate username policy пока не зафиксирована как часть канонического MVP contract.

## Scope
**Включает:**
- синхронизацию `sea_patrol_backend/ai-docs/API_INFO.md` с фактическим backend code/tests;
- фиксацию backend assumptions для auth responses/errors;
- документирование текущего ограничения по duplicate username.

**Не включает (out of scope):**
- изменение runtime-поведения auth endpoints;
- добавление `409 USER_ALREADY_EXISTS`;
- изменение frontend docs или frontend runtime-кода;
- изменение orchestration docs как primary deliverable backend repo.

## Предпосылки и зависимости
- Backend часть `TASK-001` должна быть завершена до `TASK-002`, потому что `TASK-002` уже меняет backend behavior/tests.
- Frontend часть shared-задачи оформляется отдельно в `sea_patrol_frontend/ai-docs/todo/TASK-001.md`.

## Технический подход
Для backend repo каноника фиксируется по ближайшему уровню истины: коду и тестам. Это означает, что backend docs должны описывать именно текущий response/error format, а не старые frontend ожидания. Если проект позже решит перейти на `201 Created` или `409`, это уже отдельное изменение backend behavior, а не часть базовой документарной фиксации.

## Изменения по репозиторию
### `sea_patrol_backend`
- [x] Обновить `ai-docs/API_INFO.md`
- [x] Создать `ai-docs/todo/TASK-001.md`
- [ ] Обновить `ai-docs/PROJECT_INFO.md` при необходимости
- [ ] Добавить или обновить тесты

## Контракты и данные
### Backend auth contract
- `POST /api/v1/auth/signup`
  - Request: `{ username, password, email }`
  - Response: `200 OK` + `{ username }`
- `POST /api/v1/auth/login`
  - Request: `{ username, password }`
  - Response: `{ username, token, issuedAt, expiresAt }`
- Ошибки auth/security/validation
  - Response: `{ errors: [{ code, message }] }`

### Backend ограничения
- `userId` сейчас не возвращается.
- duplicate username conflict сейчас отдельно не моделируется.
- `signup` в in-memory repository перезаписывает запись по `username`.

## Риски и меры контроля
| Риск | Почему это риск | Мера контроля |
|------|-----------------|---------------|
| Backend docs снова разойдутся с кодом | В проекте уже было противоречие по `@Valid` | Опираемся только на фактический код и `AuthControllerTest` |
| В docs случайно появится несуществующий `409` | Это создаст ложные ожидания для frontend и QA | Явно держим `409` вне текущего contract |
| Следующий backend task начнёт менять поведение без зафиксированной базы | `TASK-002` может уехать от текущих гарантий | Использовать этот task file как baseline для `TASK-002` |

## План реализации
1. Сверить backend code/tests и backend docs.
2. Убрать из backend docs устаревшие утверждения.
3. Зафиксировать backend contract как baseline для следующих задач.

## Проверки
### Автоматические проверки
| Репозиторий | Команда | Зачем | Статус |
|-------------|---------|-------|--------|
| `sea_patrol_backend` | `.\gradlew.bat test` | Проверить соответствие docs текущим backend tests | `Not Run` |

### Ручная проверка
- [x] Сверен `AuthController`
- [x] Сверен `AuthControllerTest`
- [x] Сверен error handling для auth/validation
- [x] Backend docs синхронизированы с backend contract

## Реализация
### Измененные файлы
1. `sea_patrol_backend/ai-docs/todo/TASK-001.md` - backend-specific описание shared задачи
2. `sea_patrol_backend/ai-docs/API_INFO.md` - backend auth docs выровнены по фактическому contract

### Незапланированные находки
- В backend docs было устаревшее утверждение про отсутствие `@Valid`.
- Duplicate username policy в текущем backend не оформлена как отдельный business contract.

## QA / Review
### QA
- [x] Все acceptance criteria подтверждены
- [x] Критические сценарии пройдены
- [x] Регрессии не обнаружены

**QA статус:** `Passed`

### Code Review
| Приоритет | Комментарий | Статус |
|-----------|-------------|--------|
| `Medium` | Решить отдельной задачей, нужен ли backend переход на `201 Created` | `Open` |
| `Medium` | Решить отдельной задачей, нужен ли backend conflict response для duplicate username | `Open` |

**Review решение:** `Approve`

## Финализация
- [x] Документация backend синхронизирована
- [x] Следующие backend follow-up задачи определены
- [ ] Задача перенесена в выполненные / архив

## Ссылки
- Related docs: `sea_patrol_backend/ai-docs/API_INFO.md`, `sea_patrol_orchestration/API.md`
