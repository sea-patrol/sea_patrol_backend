# TASK-035 - Backend часть: clockwise rotation room wind

## Метаданные
- **ID:** `TASK-035`
- **Тип:** `feature`
- **Статус:** `Done`
- **Приоритет:** `High`
- **Дата создания:** `2026-03-12`
- **Автор:** `Codex`
- **Связанный roadmap item:** `Wave 4 / TASK-035`
- **Трек:** `Backend`
- **Depends on:** `TASK-031`, `TASK-033`

## Контекст
После `TASK-031` backend уже держал `wind` как authoritative room state и отдавал его в `INIT_GAME_STATE` / `UPDATE_GAME_STATE`, но сама policy обновления направления оставалась случайной: `Wind.update()` вносил шум, а не давал предсказуемый drift по часовой стрелке.

## Цель
Сделать wind policy в комнате предсказуемой: backend должен вращать `wind.angle` по часовой стрелке с фиксированной room-wide скоростью и одинаково рассылать это состояние всем игрокам комнаты.

## Source of Truth
- Код:
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/Wind.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/GameRoom.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/RoomRegistry.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/GameRoomProperties.java`
  - `sea_patrol_backend/src/main/resources/application.yaml`
- Тесты:
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/game/WindTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/game/physics/GameRoomPhysicsTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/room/RoomJoinControllerTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/game/GameRoomPropertiesTest.java`
- Документация:
  - `sea_patrol_backend/ai-docs/API_INFO.md`
  - `sea_patrol_backend/ai-docs/PROJECT_INFO.md`
  - `sea_patrol_frontend/ai-docs/API_INFO.md`
  - `sea_patrol_orchestration/API.md`
  - `sea_patrol_orchestration/PROJECTS_ORCESTRATION_INFO.md`
  - `sea_patrol_orchestration/ROADMAP-TASKS.md`

## Acceptance Criteria
- [x] `wind.angle` меняется предсказуемо по часовой стрелке в backend runtime.
- [x] Скорость вращения задаётся конфигом комнаты, а не hardcoded random noise.
- [x] Все игроки комнаты продолжают получать один и тот же authoritative `wind` snapshot.
- [x] Есть tests на clockwise rotation, wrap в диапазон `[0, 2π)` и реальный room/integration flow.

## Scope
**Включает:**
- замену случайного `Wind.update()` на deterministic clockwise rotation;
- новый backend config `game.room.wind-rotation-speed`;
- протягивание config в `RoomRegistry -> GameRoom -> Wind`;
- unit/integration tests;
- синхронизацию backend/frontend/orchestration docs и roadmap.

**Не включает (out of scope):**
- изменение transport shape `WindInfo`;
- frontend-анимацию ветра вне authoritative snapshots;
- новую сложную weather system;
- изменение physics contract корабля beyond уже существующего sail/wind loop.

## Технический подход
- `Wind` теперь хранит `rotationSpeedRadPerSecond` и обновляет угол как `angle -= speed * delta`, то есть clockwise в канонической системе `XZ`.
- Угол нормализуется в диапазон `[0, 2π)`, чтобы transport не уходил в отрицательные значения или бесконечный рост.
- `GameRoomProperties` получил новый room runtime default `wind-rotation-speed`, а `RoomRegistry` создаёт `GameRoom` уже с этой политикой.
- `GameRoom` продолжает отдавать тот же `WindInfo`, но берёт угол из нового deterministic state вместо случайного дрейфа.
- MVP default в `application.yaml`: `0.17453292 rad/s` (примерно `10°/s`).

## Контракты и данные
### Конфигурация
- `game.room.wind-rotation-speed`
- env override: `GAME_WIND_ROTATION_SPEED`

### WebSocket
- `INIT_GAME_STATE.wind` shape не изменился
- `UPDATE_GAME_STATE.wind` shape не изменился
- изменилась только runtime policy изменения `angle`

## Проверки
### Автоматические проверки
| Репозиторий | Команда | Зачем | Статус |
|-------------|---------|-------|--------|
| `sea_patrol_backend` | `.\gradlew.bat test` | Проверить весь backend suite после перехода на deterministic clockwise wind | `Passed` |

### Ручная проверка
- [x] Комната со стартовым `wind.angle = PI / 2` на следующих update-тикках получает меньший угол
- [x] Скорость ветра не меняется от rotation policy
- [x] Клиент в `test-sandbox-01` видит, что `UPDATE_GAME_STATE.wind.angle < INIT_GAME_STATE.wind.angle`

## QA / Review
### QA
- [x] Все acceptance criteria подтверждены
- [x] Backend test suite проходит полностью
- [x] Документация синхронизирована

**QA статус:** `Passed`

### Code Review
| Приоритет | Комментарий | Статус |
|-----------|-------------|--------|
| `Low` | Rotation policy остаётся намеренно простой и room-wide; per-map weather scripts и переменная скорость вращения остаются следующими возможными задачами | `Resolved` |

**Review решение:** `Approve`

## Финализация
- [x] Random wind noise убран из основного runtime path
- [x] Wind rotation стал backend-authoritative и предсказуемым
- [x] Tests проходят
- [x] Документация синхронизирована
- [x] Задача помечена как выполненная в roadmap
