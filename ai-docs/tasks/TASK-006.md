# TASK-006 - Backend часть: single-session policy для game session

## Метаданные
- **ID:** `TASK-006`
- **Тип:** `feature`
- **Статус:** `Review`
- **Приоритет:** `High`
- **Дата создания:** `2026-03-06`
- **Автор:** `Codex`
- **Связанный roadmap item:** `Wave 0 / TASK-006`
- **Трек:** `Backend`
- **Depends on:** `TASK-004`

## Контекст
После фиксации room/lobby contract backend все еще позволял одному и тому же пользователю создавать несколько параллельных игровых сессий. Это ломало предпосылку для будущих room join/reconnect flows: не было единого owner текущей game session, повторный login не сигнализировал о конфликте, а WebSocket handler не различал reconnect и duplicate parallel connect.

Для следующих задач (`TASK-011`, `TASK-021`) нужен был минимальный admission layer уже сейчас: backend должен уметь сказать "эта учетная запись уже занята активной игровой сессией", но при этом не блокировать контролируемый reconnect в пределах grace window.

## Цель
Ввести single-session policy для backend game session: один пользователь может иметь только одну активную игровую WebSocket-сессию, duplicate login и duplicate parallel WS connection должны отклоняться, а reconnect в пределах config-driven grace window должен оставаться допустимым.

## Source of Truth
- Код:
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/session/GameSessionRegistry.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/auth/security/ReactiveSecurityManager.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/ws/game/GameWebSocketHandler.java`
  - `sea_patrol_backend/src/main/resources/application.yaml`
- Тесты:
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/service/session/GameSessionRegistryTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/ws/WebSocketHandshakeTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/ws/WsProtocolParsingTest.java`
- Внешний контракт:
  - `sea_patrol_orchestration/API.md`
  - `sea_patrol_backend/ai-docs/API_INFO.md`

## Acceptance Criteria
- [x] Повторная параллельная сессия отклоняется.
- [x] Reconnect в течение 30 секунд возможен.
- [x] Есть backend tests на duplicate login / duplicate WebSocket / reconnect grace.

## Scope
**Включает:**
- введение session registry для active/disconnected-grace состояний;
- запрет duplicate login, если активная game WS-сессия уже существует;
- запрет второго параллельного `/ws/game` подключения для того же `username`;
- разрешение reconnect в пределах `game.room.reconnect-grace-period`;
- тесты на новый admission policy.

**Не включает (out of scope):**
- восстановление room membership/state после reconnect;
- full reconnect/resume flow для gameplay;
- lobby/room binding после reconnect;
- room cleanup policy с учетом reconnect grace.

## Технический подход
Backend получил отдельный `GameSessionRegistry`, который ведет ownership game session по `username` и хранит два состояния: `ACTIVE` и `DISCONNECTED_GRACE`. `ReactiveSecurityManager` использует registry как admission check перед выдачей нового JWT, а `GameWebSocketHandler` захватывает ownership при handshake и отклоняет duplicate parallel connection через `POLICY_VIOLATION`.

После disconnect runtime очищает chat/game state как и раньше, но ownership username удерживается в registry до истечения grace period. Это дает безопасный минимальный single-session contract уже сейчас, не притворяясь полноценным room resume. Полный reconnect lifecycle остается отдельной задачей roadmap (`TASK-021`).

## Изменения по репозиторию
### `sea_patrol_backend`
- [x] Добавить session registry для single-session admission
- [x] Блокировать duplicate login при активной game session
- [x] Блокировать duplicate parallel WebSocket connection
- [x] Разрешить reconnect в пределах config-driven grace window
- [x] Добавить tests на duplicate/reconnect сценарии
- [x] Обновить `ai-docs/API_INFO.md`
- [x] Обновить `ai-docs/PROJECT_INFO.md`

## Контракты и данные
### Auth error contract
- `POST /api/v1/auth/login` может вернуть `401` + `{ errors: [{ code, message }] }`
- новый код ошибки: `SEAPATROL_DUPLICATE_SESSION`
- сообщение: `Active game session already exists`

### WebSocket policy
- один `username` -> одна активная `/ws/game` session
- duplicate parallel connect -> `CloseStatus.POLICY_VIOLATION`
- close reason содержит `SEAPATROL_DUPLICATE_SESSION`
- reconnect allowed only within `game.room.reconnect-grace-period`

## Риски и меры контроля
| Риск | Почему это риск | Мера контроля |
|------|-----------------|---------------|
| Reconnect semantics будут восприняты как full room resume | Registry сейчас держит только session ownership, а не полное room state | Документация явно отделяет admission policy от будущего `TASK-021` |
| Duplicate login можно обойти через race между login и WS connect | Auth и WS admission происходят в разных точках lifecycle | WS handler повторно валидирует ownership через `claimSession` |
| Grace window может протухать незаметно или зависать | В registry используется отложенная очистка по scheduler | Добавлены unit tests на expiry и reconnect within grace |

## План реализации
1. Ввести `GameSessionRegistry` с состояниями `ACTIVE` / `DISCONNECTED_GRACE`.
2. Подключить registry к auth login flow и к `/ws/game` handshake.
3. Сохранить grace window после disconnect, не смешивая это с full room resume.
4. Усилить backend tests на duplicate login, duplicate WS и reconnect grace.
5. Синхронизировать backend и orchestration docs.

## Проверки
### Автоматические проверки
| Репозиторий | Команда | Зачем | Статус |
|-------------|---------|-------|--------|
| `sea_patrol_backend` | `.\gradlew.bat test` | Проверить single-session policy и отсутствие backend regressions | `Passed` |

### Ручная проверка
- [x] Проверено, что duplicate login при активной WS-сессии возвращает structured `401` error
- [x] Проверено, что второе параллельное `/ws/game` подключение закрывается с `POLICY_VIOLATION`
- [x] Проверено, что reconnect после disconnect допускается в пределах grace window
- [x] Проверено, что reconnect grace не документируется как full room resume

## Реализация
### Измененные файлы
1. `sea_patrol_backend/src/main/java/ru/sea/patrol/service/session/GameSessionRegistry.java` - registry active/grace session ownership
2. `sea_patrol_backend/src/main/java/ru/sea/patrol/auth/security/ReactiveSecurityManager.java` - duplicate login admission check
3. `sea_patrol_backend/src/main/java/ru/sea/patrol/ws/game/GameWebSocketHandler.java` - duplicate WS rejection и disconnect registration
4. `sea_patrol_backend/src/test/java/ru/sea/patrol/service/session/GameSessionRegistryTest.java` - unit tests на duplicate/reconnect/expiry
5. `sea_patrol_backend/src/test/java/ru/sea/patrol/ws/WebSocketHandshakeTest.java` - integration tests на duplicate login и duplicate WS
6. `sea_patrol_backend/src/test/java/ru/sea/patrol/ws/WsProtocolParsingTest.java` - test wiring под новый handler dependency
7. `sea_patrol_backend/ai-docs/API_INFO.md` - синхронизирован внешний backend contract
8. `sea_patrol_backend/ai-docs/PROJECT_INFO.md` - синхронизирована backend architecture/runtime documentation
9. `sea_patrol_backend/ai-docs/tasks/TASK-006.md` - backend task artifact

### Незапланированные находки
- Для надежного duplicate-check одного запрета на `login` недостаточно: повторный контроль в самом WebSocket handler обязателен.
- Текущий reconnect grace технически совместим с будущим resume flow, но не должен уже сейчас трактоваться как восстановление room state.

## QA / Review
### QA
- [x] Все acceptance criteria подтверждены
- [x] Ключевые сценарии проходят
- [x] Регресс по backend test suite не обнаружен

**QA статус:** `Passed`

### Code Review
| Приоритет | Комментарий | Статус |
|-----------|-------------|--------|
| `Medium` | При реализации `TASK-021` нужно решить, переносится ли registry в более широкий session/room lifecycle layer, чтобы reconnect восстанавливал не только admission, но и room binding/state | `Open` |

**Review решение:** `Approve`

## Финализация
- [x] Код backend обновлен
- [x] Tests проходят
- [ ] Задача перенесена в выполненные / архив

## Ссылки
- Related docs: `sea_patrol_orchestration/ROADMAP.md`, `sea_patrol_orchestration/API.md`, `sea_patrol_backend/ai-docs/API_INFO.md`, `sea_patrol_backend/ai-docs/PROJECT_INFO.md`
