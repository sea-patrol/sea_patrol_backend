# TASK-005 - Backend часть: вынести room runtime defaults в конфигурацию

## Метаданные
- **ID:** `TASK-005`
- **Тип:** `feature`
- **Статус:** `Review`
- **Приоритет:** `High`
- **Дата создания:** `2026-03-06`
- **Автор:** `Codex`
- **Связанный roadmap item:** `Wave 0 / TASK-005`
- **Трек:** `Backend`
- **Depends on:** `TASK-004`

## Контекст
После фиксации room/lobby contract в `TASK-004` backend всё ещё держал часть базовых room defaults прямо в runtime-коде. Частота room update была захардкожена в `GameRoom`, default room name — в `GameWebSocketHandler`, а будущие MVP limits (`maxRooms`, `maxPlayersPerRoom`, reconnect grace`) вообще не имели typed configuration source.

Это создавало две проблемы: dev/MVP значения нельзя было централизованно переопределить через конфиг, а следующие backend задачи по room registry/join/reconnect пришлось бы строить поверх разрозненных хардкодов.

## Цель
Вынести базовые room/tick/reconnect defaults в typed backend configuration и подключить её в текущий runtime, чтобы room-related backend logic опиралась на один source of truth в `application.yaml`.

## Source of Truth
- Код:
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/GameRoomProperties.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/GameRoom.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/GameService.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/ws/game/GameWebSocketHandler.java`
  - `sea_patrol_backend/src/main/resources/application.yaml`
- Тесты:
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/game/GameRoomLifecycleTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/game/physics/GameRoomPhysicsTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/game/GameRoomPropertiesTest.java`
- Внешняя документация:
  - `sea_patrol_orchestration/ROADMAP.md`
  - `sea_patrol_orchestration/API.md`

## Acceptance Criteria
- [x] Room/tick/reconnect defaults вынесены в typed config properties.
- [x] Core runtime больше не хардкодит default room name и room update period.
- [x] Есть dev/MVP defaults в `application.yaml` и tests на binding/override конфигурации.

## Scope
**Включает:**
- введение `game.room.*` properties;
- перевод `GameService` и `GameWebSocketHandler` на room config;
- удаление hardcoded room update period из default runtime path;
- тесты на default и overridden room config values.

**Не включает (out of scope):**
- полноценный `RoomRegistry`;
- enforcement `maxRooms` / `maxPlayersPerRoom`;
- reconnect lifecycle implementation;
- новые REST/WS room endpoints.

## Технический подход
Вместо разрозненных констант backend теперь использует `GameRoomProperties` как typed `@ConfigurationProperties` bean с валидацией. `GameService` получает room defaults из него и создаёт комнаты с конфигурируемым `updatePeriod`, а `GameWebSocketHandler` берёт default room name из того же источника. Нереализованные пока room limits и reconnect grace тоже уже живут в конфиге, чтобы следующие backend tasks строились на готовом contract для runtime settings.

## Изменения по репозиторию
### `sea_patrol_backend`
- [x] Добавить typed room configuration properties
- [x] Перевести current runtime на config-driven default room name и tick period
- [x] Добавить tests на default/override binding
- [x] Обновить `ai-docs/PROJECT_INFO.md`
- [ ] Обновить `ai-docs/API_INFO.md` при изменении внешнего API

## Контракты и данные
### Конфигурация
- `game.room.default-room-name`
- `game.room.update-period`
- `game.room.max-rooms`
- `game.room.max-players-per-room`
- `game.room.reconnect-grace-period`

### MVP defaults
- `default-room-name = main`
- `update-period = 100ms`
- `max-rooms = 5`
- `max-players-per-room = 100`
- `reconnect-grace-period = 30s`

## Риски и меры контроля
| Риск | Почему это риск | Мера контроля |
|------|-----------------|---------------|
| Room defaults снова разъедутся по коду | Room logic сейчас всё ещё переходная и меняется по задачам roadmap | Один typed config bean + `application.yaml` как source of truth |
| Следующие room tasks начнут вводить новые хардкоды | Без общего config holder это происходит естественно | `GameService` уже даёт централизованный доступ к room defaults |
| Конфиг сломается при override через env | Duration/int binding легко сломать незаметно | Добавлены tests на default values и override values |

## План реализации
1. Ввести `GameRoomProperties` и dev/MVP defaults в `application.yaml`.
2. Подключить config в `GameService` и `GameWebSocketHandler`.
3. Убрать default hardcode из `GameRoom` runtime path и синхронизировать unit tests.
4. Добавить tests на config binding и прогнать backend suite.

## Проверки
### Автоматические проверки
| Репозиторий | Команда | Зачем | Статус |
|-------------|---------|-------|--------|
| `sea_patrol_backend` | `.\gradlew.bat test` | Проверить config binding и отсутствие регрессии backend | `Passed` |

### Ручная проверка
- [x] Проверено, что default room name теперь берётся из config
- [x] Проверено, что room update period задаётся через config values
- [x] Проверено, что `maxRooms`, `maxPlayersPerRoom`, `reconnectGracePeriod` имеют dev/MVP defaults и override binding

## Реализация
### Измененные файлы
1. `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/GameRoomProperties.java` - typed room configuration properties
2. `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/GameRoom.java` - room update period теперь передаётся явно через config-backed constructor
3. `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/GameService.java` - room defaults централизованы через properties
4. `sea_patrol_backend/src/main/java/ru/sea/patrol/ws/game/GameWebSocketHandler.java` - default room name больше не захардкожен
5. `sea_patrol_backend/src/main/resources/application.yaml` - добавлены dev/MVP defaults для `game.room.*`
6. `sea_patrol_backend/src/test/java/ru/sea/patrol/game/GameRoomLifecycleTest.java` - обновлён под явный room tick config
7. `sea_patrol_backend/src/test/java/ru/sea/patrol/game/physics/GameRoomPhysicsTest.java` - обновлён под явный room tick config
8. `sea_patrol_backend/src/test/java/ru/sea/patrol/game/GameRoomPropertiesTest.java` - tests на defaults и override binding
9. `sea_patrol_backend/ai-docs/PROJECT_INFO.md` - синхронизирована backend documentation по room config
10. `sea_patrol_backend/ai-docs/tasks/TASK-005.md` - backend task artifact

### Незапланированные находки
- Default room name тоже уже является важным runtime default и логично живёт рядом с room limits, хотя изначально roadmap явно акцентировал только tick/limits/reconnect.
- Полный enforcement `maxRooms` и reconnect grace в runtime пока отсутствует, но конфигурационная база под следующие задачи уже готова.

## QA / Review
### QA
- [x] Все acceptance criteria подтверждены
- [x] Ключевые сценарии проходят
- [x] Регресс по backend test suite не обнаружен

**QA статус:** `Passed`

### Code Review
| Приоритет | Комментарий | Статус |
|-----------|-------------|--------|
| `Medium` | Когда начнётся `TASK-008`/`TASK-011`, стоит перенести доступ к room config из `GameService` в отдельный room registry / admission layer | `Open` |

**Review решение:** `Approve`

## Финализация
- [x] Код backend обновлён
- [x] Tests проходят
- [ ] Задача перенесена в выполненные / архив

## Ссылки
- Related docs: `sea_patrol_orchestration/ROADMAP.md`, `sea_patrol_orchestration/API.md`, `sea_patrol_backend/ai-docs/PROJECT_INFO.md`
