# TASK-018 - Backend часть: authoritative initial spawn calculation

## Метаданные
- **ID:** `TASK-018`
- **Тип:** `feature`
- **Статус:** `Review`
- **Приоритет:** `High`
- **Дата создания:** `2026-03-09`
- **Автор:** `Codex`
- **Связанный roadmap item:** `Wave 2 / TASK-018`
- **Трек:** `Backend`
- **Depends on:** `TASK-011`

## Контекст
После `TASK-011` backend уже отправлял `SPAWN_ASSIGNED`, но payload был placeholder-значением `(0.0, 0.0, 0.0)`. Это не соответствовало целям Wave 2: spawn должен вычисляться сервером, лежать в допустимых bounds и совпадать с фактическим initial state игрока в комнате.

## Цель
Сделать initial spawn server-authoritative: backend сам вычисляет координаты около `(0, 0)`, валидирует их по MVP bounds и использует один и тот же spawn как для `SPAWN_ASSIGNED`, так и для последующего `INIT_GAME_STATE`.

## Source of Truth
- Код:
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/SpawnService.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/SpawnPoint.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/GameService.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/RoomJoinService.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/PlayerShipInstance.java`
- Тесты:
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/game/SpawnServiceTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/room/RoomJoinControllerTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/ws/WsProtocolParsingTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/game/GameRoomCleanupPolicyTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/service/session/GameSessionRegistryTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/game/RoomRegistryTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/game/GameRoomPropertiesTest.java`
- Документация:
  - `sea_patrol_backend/ai-docs/API_INFO.md`
  - `sea_patrol_backend/ai-docs/PROJECT_INFO.md`
  - `sea_patrol_frontend/ai-docs/API_INFO.md`
  - `sea_patrol_orchestration/API.md`
  - `sea_patrol_orchestration/PROJECTS_ORCESTRATION_INFO.md`
  - `sea_patrol_orchestration/ROADMAP-TASKS.md`

## Acceptance Criteria
- [x] Initial spawn вычисляет только backend.
- [x] Spawn лежит в допустимых bounds.
- [x] `SPAWN_ASSIGNED` и `INIT_GAME_STATE` для текущего игрока используют один и тот же authoritative spawn.
- [x] Есть тест на диапазон координат.

## Scope
**Включает:**
- отдельный backend service для расчёта initial spawn;
- server-side bounds validation для initial spawn;
- применение authoritative spawn к runtime player state и ship transform;
- integration test на room join sequence с проверкой диапазона координат и совпадения с `INIT_GAME_STATE`;
- синхронизацию backend/orchestration docs под новый runtime.

**Не включает (out of scope):**
- respawn flow и `SPAWN_ASSIGNED(reason=RESPAWN)` из `TASK-019`;
- map-specific spawn points и `MapTemplateRegistry`;
- reconnect resume room state из `TASK-021`.

## Технический подход
`SpawnService` генерирует random offset вокруг `(0, 0)` и принимает только координаты, попадающие в текущие MVP bounds `x/z in [-30.0, 30.0]`. `GameService.assignInitialSpawn(...)` применяет этот spawn к `Player` и, если ship instance уже существует, синхронизирует Box2D transform через frontend coordinate mapping. `RoomJoinService` отправляет `SPAWN_ASSIGNED` уже с реальными authoritative координатами до `INIT_GAME_STATE`.

Отдельно выровнена coordinate mapping semantics: initial ship transform и последующий room snapshot теперь опираются на один и тот же frontend-facing `x/z`, чтобы клиент не видел рассинхрон между `SPAWN_ASSIGNED` и первым `INIT_GAME_STATE`.

## Контракты и данные
### `SPAWN_ASSIGNED`
```json
{
  "roomId": "sandbox-1",
  "reason": "INITIAL",
  "x": 12.5,
  "z": -8.0,
  "angle": 0.0
}
```

### Правила текущей реализации
- initial spawn считается только на backend;
- текущие MVP bounds: `x/z in [-30.0, 30.0]`;
- `angle` для initial spawn пока фиксирован как `0.0`;
- payload `SPAWN_ASSIGNED` должен совпадать с координатами current player в следующем `INIT_GAME_STATE`.

## Проверки
### Автоматические проверки
| Репозиторий | Команда | Зачем | Статус |
|-------------|---------|-------|--------|
| `sea_patrol_backend` | `.\\gradlew.bat test` | Проверить spawn calculation, room join sequence и отсутствие регресса по backend suite | `Passed` |

### Ручная проверка
- [x] `POST /api/v1/rooms/{roomId}/join` приводит к `ROOM_JOINED -> SPAWN_ASSIGNED -> INIT_GAME_STATE`
- [x] `SPAWN_ASSIGNED.x/z` попадает в диапазон `[-30.0, 30.0]`
- [x] `INIT_GAME_STATE.players[currentUser]` использует тот же `x/z/angle`, что и `SPAWN_ASSIGNED`

## QA / Review
### QA
- [x] Все acceptance criteria подтверждены
- [x] Есть unit coverage для spawn bounds
- [x] Есть integration coverage для authoritative room join spawn

**QA статус:** `Passed`

### Code Review
| Приоритет | Комментарий | Статус |
|-----------|-------------|--------|
| `Low` | Bounds и origin пока захардкожены в `SpawnService`. Если потребуется room/map-specific spawn policy, это стоит вынести в отдельную конфигурацию или map registry поверх будущих задач по картам | `Open` |
| `Low` | `TASK-019` всё ещё нужен отдельно: сейчас authoritative coordinates уже есть, но respawn flow ещё не реализован как отдельный runtime path | `Open` |

**Review решение:** `Approve`

## Финализация
- [x] Код backend обновлен
- [x] Tests проходят
- [ ] Задача перенесена в выполненные / архив
