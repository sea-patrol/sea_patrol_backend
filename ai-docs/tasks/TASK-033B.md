# TASK-033B - Backend часть: server-authoritative sailLevel

## Метаданные
- **ID:** `TASK-033B`
- **Тип:** `feature`
- **Статус:** `Done`
- **Приоритет:** `High`
- **Дата создания:** `2026-03-12`
- **Автор:** `Codex`
- **Связанный roadmap item:** `Wave 4 / TASK-033B`
- **Трек:** `Backend`
- **Depends on:** `TASK-033A`, `TASK-033`

## Контекст
После `TASK-033A` каноника уровней парусов уже была зафиксирована на уровне orchestration, но backend runtime всё ещё жил в старой модели: `PLAYER_INPUT.up/down` использовались как throttle/reverse, а player state не содержал `sailLevel`.

## Цель
Реализовать на backend server-authoritative `sailLevel 0..3`, сделать `up/down` командами изменения уровня парусов по rising-edge и включить это состояние в room transport.

## Source of Truth
- Код:
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/Player.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/PlayerShipInstance.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/GameRoom.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/ws/protocol/dto/PlayerInfo.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/ws/protocol/dto/PlayerUpdateInfo.java`
- Тесты:
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/game/physics/PlayerShipInstancePhysicsTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/room/RoomJoinControllerTest.java`
- Документация:
  - `sea_patrol_backend/ai-docs/API_INFO.md`
  - `sea_patrol_backend/ai-docs/PROJECT_INFO.md`
  - `sea_patrol_orchestration/API.md`
  - `sea_patrol_orchestration/PROJECTS_ORCESTRATION_INFO.md`
  - `sea_patrol_orchestration/ROADMAP-TASKS.md`

## Acceptance Criteria
- [x] Backend хранит `sailLevel` как server-authoritative состояние игрока в диапазоне `0..3`.
- [x] `PLAYER_INPUT.up/down` обрабатываются по rising-edge как `+1/-1`, а не как throttle/reverse.
- [x] При `sailLevel = 0` корабль не набирает ход от парусов.
- [x] При `sailLevel = 1..3` тяга от ветра возрастает ступенчато.
- [x] `INIT_GAME_STATE` и `UPDATE_GAME_STATE` уже несут `sailLevel` в player state.
- [x] Есть backend tests на edge handling, влияние `sailLevel` на движение и room WS snapshots.

## Scope
**Включает:**
- новое поле `sailLevel` в runtime модели игрока и ship instance;
- clamp `0..3` и default `3`;
- rising-edge обработку `PLAYER_INPUT.up/down`;
- использование `sailLevel` в формуле sail drive;
- синхронизацию `sailLevel` в `INIT_GAME_STATE` / `UPDATE_GAME_STATE`;
- обновление physics/integration tests;
- синхронизацию backend/orchestration docs.

**Не включает (out of scope):**
- frontend state/HUD consumption `sailLevel`;
- изменение room wind policy;
- сложную аэродинамику парусов;
- новые transport message types.

## Технический подход
- `Player` теперь хранит `sailLevel = 3` как server-authoritative state.
- `PlayerShipInstance` держит локальную копию этого уровня, клампит её в `0..3` и меняет только на rising-edge `up/down`.
- Итоговая тяга больше не зависит от прямого throttle input: она считается как `wind drive * sailLevel factor`.
- Для `sailLevel` используются ступенчатые коэффициенты `0.0 / 0.35 / 0.7 / 1.0`.
- Во время reconnect grace `freeze()` теперь не только сбрасывает скорости, но и блокирует drift ship instance до следующего input, чтобы retained room state не смещался сам по себе.

## Контракты и данные
### Player state
- `sailLevel`: `0 | 1 | 2 | 3`
- `0`: паруса полностью убраны
- `3`: все паруса подняты

### WebSocket
- `INIT_GAME_STATE.players[*].sailLevel` уже приходит из backend runtime
- `UPDATE_GAME_STATE.players[*].sailLevel` уже приходит как часть текущего player snapshot

## Проверки
### Автоматические проверки
| Репозиторий | Команда | Зачем | Статус |
|-------------|---------|-------|--------|
| `sea_patrol_backend` | `.\gradlew.bat test` | Проверить backend suite после внедрения `sailLevel` runtime | `Passed` |

### Ручная проверка
- [x] `sailLevel = 0` убирает sail-driven ускорение
- [x] `sailLevel 1 < 2 < 3` даёт возрастающую скорость при одном и том же ветре
- [x] удержание `up/down` не меняет уровень парусов каждый tick
- [x] reconnect resume не создаёт новый spawn и не даёт retained ship drift во время grace

## QA / Review
### QA
- [x] Все acceptance criteria подтверждены
- [x] Backend test suite проходит полностью
- [x] Документация синхронизирована

**QA статус:** `Passed`

### Code Review
| Приоритет | Комментарий | Статус |
|-----------|-------------|--------|
| `Low` | `UPDATE_GAME_STATE` сейчас несёт `sailLevel` как часть полного player snapshot, а не как sparse field-only patch; это согласовано с текущей backend моделью room updates | `Resolved` |

**Review решение:** `Approve`

## Финализация
- [x] Backend runtime переведён на server-authoritative `sailLevel`
- [x] `INIT_GAME_STATE` и `UPDATE_GAME_STATE` синхронизируют уровень парусов
- [x] Tests проходят
- [x] Документация синхронизирована
- [x] Задача помечена как выполненная в roadmap
