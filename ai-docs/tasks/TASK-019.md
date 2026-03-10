# TASK-019 - Backend часть: typed SPAWN_ASSIGNED для initial и respawn

## Метаданные
- **ID:** `TASK-019`
- **Тип:** `feature`
- **Статус:** `Done`
- **Приоритет:** `High`
- **Дата создания:** `2026-03-09`
- **Автор:** `Codex`
- **Связанный roadmap item:** `Wave 2 / TASK-019`
- **Трек:** `Backend`
- **Depends on:** `TASK-018`

## Контекст
После `TASK-018` backend уже вычислял authoritative initial spawn и отправлял `SPAWN_ASSIGNED` в join flow, но `reason` всё ещё оставался строковым magic value, а respawn path как отдельная server-side точка переиспользования transport contract ещё не был оформлен. Это оставляло риск, что будущий death/respawn flow начнёт дублировать spawn emit logic или тихо разойдётся с initial join behavior.

## Цель
Зафиксировать `SPAWN_ASSIGNED` как единый typed backend transport path для двух причин: `INITIAL` и `RESPAWN`, чтобы initial join и будущий respawn использовали один payload shape и один server-authoritative spawn calculation flow.

## Source of Truth
- Код:
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/ws/protocol/dto/SpawnReason.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/ws/protocol/dto/SpawnAssignedResponseDto.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/GameService.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/RoomJoinService.java`
- Тесты:
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/room/RoomJoinControllerTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/game/GameServiceSpawnAssignedTest.java`
- Документация:
  - `sea_patrol_backend/ai-docs/API_INFO.md`
  - `sea_patrol_backend/ai-docs/PROJECT_INFO.md`
  - `sea_patrol_frontend/ai-docs/API_INFO.md`
  - `sea_patrol_orchestration/API.md`
  - `sea_patrol_orchestration/PROJECTS_ORCESTRATION_INFO.md`

## Acceptance Criteria
- [x] `SPAWN_ASSIGNED` уходит при initial spawn.
- [x] `SPAWN_ASSIGNED` уходит при respawn.
- [x] Payload соответствует API contract: `roomId`, `reason`, `x`, `z`, `angle`.

## Scope
**Включает:**
- typed `SpawnReason` (`INITIAL`, `RESPAWN`) в backend transport DTO;
- единый emit/apply flow для authoritative spawn assignment;
- отдельный backend respawn hook для active room player с `reason=RESPAWN`;
- tests на initial join message и respawn message;
- синхронизацию backend/orchestration/frontend docs под typed spawn contract.

**Не включает (out of scope):**
- combat/death detection и auto-trigger respawn по потоплению;
- frontend respawn application (`TASK-020`);
- room resume/reconnect respawn semantics.

## Технический подход
`SpawnAssignedResponseDto` переведён на typed `SpawnReason`, а `GameService` теперь держит один server-authoritative path для назначения и эмита spawn: `emitInitialSpawnAssigned(...)` для join flow и `respawnPlayer(...)` для будущего gameplay caller. Оба варианта используют один и тот же `SpawnService` и один payload shape.

`RoomJoinService` больше не собирает `SPAWN_ASSIGNED` вручную: join flow теперь просто вызывает `emitInitialSpawnAssigned(...)`. Respawn hook для active room player сбрасывает health до `maxHealth`, обнуляет velocity и отправляет `SPAWN_ASSIGNED { reason: RESPAWN }`. Полноценный death/combat trigger остаётся задачей следующих wave'ов, но transport/runtime foundation уже готов.

## Контракты и данные
### `SPAWN_ASSIGNED`
```json
{
  "roomId": "sandbox-1",
  "reason": "INITIAL | RESPAWN",
  "x": 12.5,
  "z": -8.0,
  "angle": 0.0
}
```

### Правила текущей реализации
- `INITIAL` уже используется в room join flow;
- `RESPAWN` уже эмитится через отдельный backend respawn path для active room player;
- обе причины используют один server-authoritative spawn calculation;
- actual death/combat caller для `RESPAWN` появится в следующих backend задачах.

## Проверки
### Автоматические проверки
| Репозиторий | Команда | Зачем | Статус |
|-------------|---------|-------|--------|
| `sea_patrol_backend` | `.\\gradlew.bat test` | Проверить initial/respawn spawn contract и отсутствие регресса по backend suite | `Passed` |

### Ручная проверка
- [x] Room join по-прежнему шлёт `ROOM_JOINED -> SPAWN_ASSIGNED -> INIT_GAME_STATE`
- [x] Respawn hook шлёт `SPAWN_ASSIGNED` с `reason=RESPAWN`
- [x] Payload остаётся совместимым с frontend contract

## QA / Review
### QA
- [x] Все acceptance criteria подтверждены
- [x] Есть backend integration coverage на initial flow
- [x] Есть backend unit/runtime coverage на respawn emission path

**QA статус:** `Passed`

### Code Review
| Приоритет | Комментарий | Статус |
|-----------|-------------|--------|
| `Low` | `RESPAWN` transport path уже готов, но до задач по combat/death он вызывается только из backend service/tests, а не из реального gameplay event | `Open` |
| `Low` | Respawn reset сейчас минимальный: health/velocity reset без отдельной экономики, боезапаса и штрафов. Это соответствует roadmap, но richer combat-state reset всё ещё впереди | `Open` |

**Review решение:** `Approve`

## Финализация
- [x] Код backend обновлен
- [x] Tests проходят
- [x] Документация синхронизирована
- [ ] Задача перенесена в выполненные / архив
