# TASK-031 - Backend часть: authoritative wind state в комнате

## Метаданные
- **ID:** `TASK-031`
- **Тип:** `feature`
- **Статус:** `Done`
- **Приоритет:** `High`
- **Дата создания:** `2026-03-12`
- **Автор:** `Codex`
- **Связанный roadmap item:** `Wave 4 / TASK-031`
- **Трек:** `Backend`
- **Depends on:** `TASK-030`, `TASK-026`

## Контекст
После `TASK-026` backend уже начал включать `wind` в `INIT_GAME_STATE` и `UPDATE_GAME_STATE`, но это поведение ещё не было явно закреплено как backend deliverable `Wave 4`: не хватало чёткой формулировки про room-level authoritative state и тестов, которые доказывают, что один и тот же wind snapshot получают все игроки комнаты.

## Цель
Довести backend wind path до завершённого инкремента `Wave 4`: `GameRoom` хранит authoritative wind state, а transport `INIT_GAME_STATE` / `UPDATE_GAME_STATE` стабильно несёт его всем игрокам комнаты.

## Source of Truth
- Код:
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/GameRoom.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/Wind.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/ws/protocol/dto/InitGameStateMessage.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/ws/protocol/dto/UpdateGameStateMessage.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/ws/protocol/dto/WindInfo.java`
- Тесты:
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/game/physics/GameRoomPhysicsTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/room/RoomJoinControllerTest.java`
- Документация:
  - `sea_patrol_backend/ai-docs/API_INFO.md`
  - `sea_patrol_backend/ai-docs/PROJECT_INFO.md`
  - `sea_patrol_orchestration/API.md`
  - `sea_patrol_orchestration/PROJECTS_ORCESTRATION_INFO.md`
  - `sea_patrol_orchestration/ROADMAP-TASKS.md`

## Acceptance Criteria
- [x] `GameRoom` хранит room-level wind state и включает его в `INIT_GAME_STATE`.
- [x] `UPDATE_GAME_STATE` несёт тот же authoritative `wind` payload shape.
- [x] Тестами подтверждено, что игроки одной комнаты получают согласованный wind snapshot.

## Scope
**Включает:**
- закрепление `wind` как room-level authoritative runtime state;
- тесты на `INIT_GAME_STATE.wind` и `UPDATE_GAME_STATE.wind`;
- синхронизацию backend/orchestration docs.

**Не включает (out of scope):**
- изменение physics-модели движения корабля от ветра;
- fixed clockwise rotation policy;
- frontend consumption этого wind state.

## Технический подход
Новый transport path не вводился: backend уже использует `WindInfo` внутри `InitGameStateMessage` и `UpdateGameStateMessage`. В рамках задачи я закрепил это как завершённый backend behavior: `GameRoom` остаётся единым источником room wind state, а проверки идут на двух уровнях.

1. Physics/unit test подтверждает, что комната эмитит `wind` в `INIT_GAME_STATE` и что два игрока комнаты получают один и тот же `UPDATE_GAME_STATE.wind`.
2. Integration test подтверждает реальный WebSocket flow: после `ROOM_JOINED -> SPAWN_ASSIGNED -> INIT_GAME_STATE` клиент получает и `UPDATE_GAME_STATE` с тем же room-level wind contract.

## Контракты и данные
### WebSocket
- `INIT_GAME_STATE.wind` и `UPDATE_GAME_STATE.wind` используют один DTO `WindInfo`.
- `wind` остаётся authoritative snapshot-полем комнаты.

### Runtime
- Источник wind state: `GameRoom`
- Начальные значения: `MapTemplate.defaultWind`

## Проверки
### Автоматические проверки
| Репозиторий | Команда | Зачем | Статус |
|-------------|---------|-------|--------|
| `sea_patrol_backend` | `.\gradlew.bat test` | Проверить backend suite после закрепления wind state tests | `Passed` |

### Ручная проверка
- [x] `INIT_GAME_STATE` содержит `wind`
- [x] `UPDATE_GAME_STATE` содержит `wind`
- [x] Два игрока одной комнаты получают согласованный wind snapshot

## QA / Review
### QA
- [x] Все acceptance criteria подтверждены
- [x] Backend test suite проходит полностью
- [x] Документация синхронизирована

**QA статус:** `Passed`

### Code Review
| Приоритет | Комментарий | Статус |
|-----------|-------------|--------|
| `Low` | Clockwise rotation policy сознательно оставлена в `TASK-035`, чтобы не смешивать transport/state и следующую фазу симуляции | `Resolved` |

**Review решение:** `Approve`

## Финализация
- [x] Wind state закреплён как backend room-level runtime behavior
- [x] Tests проходят
- [x] Документация синхронизирована
- [x] Задача помечена как выполненная в roadmap
