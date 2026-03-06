# TASK-002 - Backend часть: стабилизация auth error contract

## Метаданные
- **ID:** `TASK-002`
- **Тип:** `feature`
- **Статус:** `Review`
- **Приоритет:** `High`
- **Дата создания:** `2026-03-06`
- **Автор:** `Codex`
- **Связанный roadmap item:** `Wave 0 / TASK-002`
- **Трек:** `Backend`
- **Depends on:** `TASK-001`

## Контекст
После `TASK-001` канонический auth contract был зафиксирован документарно, но на стороне backend его техническая реализация оставалась размазанной по нескольким местам: security entry point, validation handler и global error handler собирали JSON response разными способами. Это повышало риск незаметного дрейфа формата ошибок при следующих изменениях.

Кроме этого, auth tests уже существовали, но не фиксировали весь контракт достаточно жёстко: не проверяли отсутствие лишних полей (`userId`, `id`, `email`) и не везде валидировали structured error payload с `code + message`.

## Цель
Сделать backend auth responses и auth error payload стабильными на уровне реализации и тестов, чтобы дальнейшие задачи (`TASK-003` и последующие auth/lobby изменения) опирались на один и тот же фактический contract.

## Source of Truth
- Код:
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/config/WebSecurityConfig.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/error/api/AppErrorAttributes.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/error/api/AppErrorWebExceptionHandler.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/error/api/ValidationErrorHandler.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/auth/api/AuthController.java`
- Тесты:
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/auth/AuthControllerTest.java`
- Внешний контракт:
  - `sea_patrol_orchestration/API.md`
  - `sea_patrol_backend/ai-docs/API_INFO.md`

## Acceptance Criteria
- [x] `signup/login` возвращают ожидаемый JSON по каноническому контракту.
- [x] Ошибки auth/security/validation отдаются в одном формате `{ errors: [{ code, message }] }`.
- [x] Есть backend tests на success/failure cases, которые фиксируют и payload success, и payload errors.

## Scope
**Включает:**
- унификацию error envelope через общие typed DTO;
- синхронизацию serialization для security, validation и app-level auth errors;
- усиление auth integration tests по success/failure payload.

**Не включает (out of scope):**
- изменение самого канонического контракта;
- введение `409 USER_ALREADY_EXISTS`;
- изменение frontend parsing/auth state;
- новые auth endpoints.

## Технический подход
Вместо ручной сборки `Map` в нескольких местах backend теперь использует общие типы `ApiError` и `ApiErrorResponse`. Это делает формат ошибок единым не по договорённости, а на уровне кода. Поверх этого тесты усилены так, чтобы контракт был зафиксирован не только по статус-кодам, но и по структуре JSON.

## Изменения по репозиторию
### `sea_patrol_backend`
- [x] Добавить общие DTO для error envelope
- [x] Привести security/validation/app errors к одному payload
- [x] Усилить auth integration tests
- [ ] Обновить `ai-docs/API_INFO.md` при изменении внешнего контракта

## Контракты и данные
### Success payloads
- `POST /api/v1/auth/signup` -> `200 OK` + `{ username }`
- `POST /api/v1/auth/login` -> `{ username, token, issuedAt, expiresAt }`

### Error payload
- `401` / `400` auth-related cases -> `{ errors: [{ code, message }] }`
- единый envelope теперь формируется через `ApiErrorResponse`

## Риски и меры контроля
| Риск | Почему это риск | Мера контроля |
|------|-----------------|---------------|
| Формат ошибки снова разъедется между security и validation | Раньше он собирался в разных местах вручную | Общие typed DTO + один и тот же envelope во всех ветках |
| Success payload незаметно расширится лишними полями | Это сломает frontend expectations или добавит двусмысленность | Тесты проверяют отсутствие `userId`, `id`, `email` там, где их быть не должно |
| Следующие auth-изменения сломают contract незаметно | Без жёстких integration tests это трудно заметить | AuthControllerTest теперь валидирует success/failure contract подробнее |

## План реализации
1. Ввести общие error DTO.
2. Привести security, validation и app-level handlers к единому envelope.
3. Усилить auth integration tests по success/error payload.
4. Прогнать backend test suite.

## Проверки
### Автоматические проверки
| Репозиторий | Команда | Зачем | Статус |
|-------------|---------|-------|--------|
| `sea_patrol_backend` | `.\gradlew.bat test` | Проверить auth contract и регрессию backend | `Passed` |

### Ручная проверка
- [x] Проверены success payloads для `signup` и `login`
- [x] Проверены unauthorized/auth/validation error payloads
- [x] Проверен единый envelope для security и controller-level ошибок

## Реализация
### Измененные файлы
1. `sea_patrol_backend/src/main/java/ru/sea/patrol/error/api/ApiError.java` - единичная ошибка как typed DTO
2. `sea_patrol_backend/src/main/java/ru/sea/patrol/error/api/ApiErrorResponse.java` - общий envelope `{ errors: [...] }`
3. `sea_patrol_backend/src/main/java/ru/sea/patrol/config/WebSecurityConfig.java` - security errors переведены на общий envelope
4. `sea_patrol_backend/src/main/java/ru/sea/patrol/error/api/AppErrorAttributes.java` - app-level errors переведены на общий envelope
5. `sea_patrol_backend/src/main/java/ru/sea/patrol/error/api/AppErrorWebExceptionHandler.java` - сериализация общего envelope
6. `sea_patrol_backend/src/main/java/ru/sea/patrol/error/api/ValidationErrorHandler.java` - validation errors переведены на общий envelope
7. `sea_patrol_backend/src/test/java/ru/sea/patrol/auth/AuthControllerTest.java` - усилены проверки success/failure contract
8. `sea_patrol_backend/ai-docs/todo/TASK-002.md` - backend task artifact

### Незапланированные находки
- Реальный формат auth errors уже был близок к канонике, но не был жёстко зафиксирован едиными DTO.
- Основной риск был не в текущем поведении, а в дальнейшем дрейфе формата из-за трёх разных веток сборки error response.

## QA / Review
### QA
- [x] Все acceptance criteria подтверждены
- [x] Ключевые сценарии проходят
- [x] Регресс по backend test suite не обнаружен

**QA статус:** `Passed`

### Code Review
| Приоритет | Комментарий | Статус |
|-----------|-------------|--------|
| `Medium` | Если позже появятся multi-error payloads вне validation, проверить, остаётся ли `ApiErrorResponse` достаточным контейнером | `Open` |

**Review решение:** `Approve`

## Финализация
- [x] Код backend обновлён
- [x] Tests проходят
- [ ] Задача перенесена в выполненные / архив

## Ссылки
- Related docs: `sea_patrol_orchestration/API.md`, `sea_patrol_backend/ai-docs/API_INFO.md`
