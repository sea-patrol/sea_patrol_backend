# TASK-022B - Backend часть: empty-room idle timeout

## Метаданные
- **ID:** `TASK-022B`
- **Тип:** `feature`
- **Статус:** `Done`
- **Приоритет:** `High`
- **Дата создания:** `2026-03-09`
- **Автор:** `Codex`
- **Связанный roadmap item:** `Wave 2 / TASK-022B`
- **Трек:** `Backend`
- **Depends on:** `TASK-012`, `TASK-021`

## Контекст
После `TASK-012` и `TASK-021` backend уже корректно удерживал комнату на время reconnect grace и умел возвращать игрока в ту же room session. Но дальше lifecycle оставался слишком резким и непоследовательным: пустая комната удалялась сразу после final cleanup retained player, а комнаты, которые были созданы, но в них никто не зашёл, могли висеть без явной bounded policy.

## Цель
Сделать lifecycle пустых комнат предсказуемым: backend должен держать отдельный idle timeout для комнат с `0` игроков и без reconnect grace, автоматически закрывать `created-but-never-joined` комнаты и публиковать это удаление в lobby catalog.

## Source of Truth
- Код:
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/GameRoomProperties.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/RoomRegistry.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/EmptyRoomExpiredEvent.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/EmptyRoomCleanupListener.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/GameService.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/session/GameSessionRegistry.java`
  - `sea_patrol_backend/src/main/resources/application.yaml`
  - `sea_patrol_backend/src/test/resources/application.yaml`
- Тесты:
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/game/RoomRegistryTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/game/GameRoomCleanupPolicyTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/service/session/GameSessionRegistryTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/room/RoomCatalogWsUpdatesTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/game/GameRoomPropertiesTest.java`
- Документация:
  - `sea_patrol_backend/ai-docs/API_INFO.md`
  - `sea_patrol_backend/ai-docs/PROJECT_INFO.md`
  - `sea_patrol_orchestration/API.md`
  - `sea_patrol_orchestration/PROJECTS_ORCESTRATION_INFO.md`
  - `sea_patrol_orchestration/ROADMAP-TASKS.md`
  - `sea_patrol_frontend/ai-docs/API_INFO.md`

## Acceptance Criteria
- [x] Комната, созданная и оставшаяся пустой, автоматически закрывается после idle timeout.
- [x] Комната, из которой ушли все игроки и закончился reconnect grace, автоматически закрывается после idle timeout.
- [x] Lobby catalog получает обновление и не оставляет мёртвые комнаты в списке.

## Scope
**Включает:**
- новый config key `game.room.empty-room-idle-timeout`;
- scheduler-driven cleanup пустых комнат внутри `RoomRegistry`;
- отдельное событие удаления комнаты для публикации `ROOMS_UPDATED`;
- отмену cleanup, если в комнату успели войти до истечения timeout;
- обновление integration/unit tests и docs.

**Не включает (out of scope):**
- manual close room API;
- owner/host semantics для комнаты;
- frontend create-and-join UX (`TASK-022C`).

## Технический подход
`RoomRegistry` теперь держит собственный scheduler для пустых комнат. При создании комнаты или после final leave/grace cleanup backend не удаляет её мгновенно, а переводит в idle-empty состояние и планирует cleanup через `game.room.empty-room-idle-timeout`. Если за это время в комнату входит игрок, pending cleanup отменяется.

Когда scheduled cleanup действительно удаляет room entry, `RoomRegistry` публикует `EmptyRoomExpiredEvent`, а отдельный listener триггерит `ROOMS_UPDATED` для lobby клиентов. Это позволяет держать каталог консистентным и для autonomous timer-based cleanup, а не только для REST/WS flows, инициированных пользователем.

## Контракты и данные
### Конфигурация
- `game.room.reconnect-grace-period` default: `15s`.
- `game.room.empty-room-idle-timeout` default: `30s`.

### Room catalog semantics
- После final leave или grace expiry lobby catalog может сначала увидеть комнату с `currentPlayers = 0`.
- Если никто не входит обратно, backend удаляет комнату отдельным `ROOMS_UPDATED` после idle timeout.
- `created-but-never-joined` room тоже подчиняется той же policy.

## Проверки
### Автоматические проверки
| Репозиторий | Команда | Зачем | Статус |
|-------------|---------|-------|--------|
| `sea_patrol_backend` | `\.\gradlew.bat test` | Проверить empty-room lifecycle, reconnect/grace cleanup и отсутствие регрессий по backend suite | `Passed` |

### Ручная проверка
- [x] Пустая freshly created room исчезает по timeout
- [x] Пустая room после grace expiry не удаляется мгновенно, а исчезает по timeout
- [x] Lobby WS получает обновление при фактическом удалении комнаты

## QA / Review
### QA
- [x] Все acceptance criteria подтверждены
- [x] Backend test suite проходит полностью
- [x] Backend/orchestration/frontend docs синхронизированы

**QA статус:** `Passed`

### Code Review
| Приоритет | Комментарий | Статус |
|-----------|-------------|--------|
| `Low` | Create-and-join UX всё ещё живёт отдельной задачей `TASK-022C`; пока freshly created room может успеть появиться и исчезнуть без участия пользователя, что теперь уже bounded policy, но не конечный UX | `Open` |

**Review решение:** `Approve`

## Финализация
- [x] Backend runtime обновлен
- [x] Tests проходят
- [x] Документация синхронизирована
- [x] Задача помечена как выполненная в roadmap

