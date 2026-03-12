# TASK-026 - Backend часть: room bootstrap от map template

## Метаданные
- **ID:** `TASK-026`
- **Тип:** `feature`
- **Статус:** `Done`
- **Приоритет:** `High`
- **Дата создания:** `2026-03-12`
- **Автор:** `Codex`
- **Связанный roadmap item:** `Wave 3 / TASK-026`
- **Трек:** `Backend`
- **Depends on:** `TASK-023`, `TASK-008`

## Контекст
После `TASK-023`..`TASK-025` backend уже умел загружать полноценные map packages и валидировать `mapId`, но room runtime всё ещё стартовал на hardcoded defaults: `GameRoom` не знал свой `MapTemplate`, initial spawn считался вокруг `(0, 0)` по фиксированным bounds, а ветер стартовал из статического значения, не из карты.

## Цель
Сделать `MapTemplate` реальным source of truth для bootstrap комнаты, чтобы runtime комнаты, spawn policy и initial room metadata брались из данных карты, а не из хардкода.

## Source of Truth
- Код / ресурсы:
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/GameRoom.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/GameService.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/RoomRegistry.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/RoomCatalogService.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/SpawnService.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/Wind.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/ws/protocol/dto/InitGameStateMessage.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/ws/protocol/dto/RoomStateInfo.java`
- Тесты:
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/game/SpawnServiceTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/room/RoomJoinControllerTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/game/GameServiceSpawnAssignedTest.java`
- Документация:
  - `sea_patrol_backend/ai-docs/API_INFO.md`
  - `sea_patrol_backend/ai-docs/PROJECT_INFO.md`
  - `sea_patrol_orchestration/API.md`
  - `sea_patrol_orchestration/PROJECTS_ORCESTRATION_INFO.md`
  - `sea_patrol_orchestration/ROADMAP-TASKS.md`
  - `sea_patrol_frontend/ai-docs/API_INFO.md`

## Acceptance Criteria
- [x] Room runtime знает, из какого `MapTemplate` он создан.
- [x] `INIT_GAME_STATE` опирается на map-driven room metadata.
- [x] Initial spawn и initial wind defaults берутся из карты.

## Scope
**Включает:**
- map-aware `GameRoom` bootstrap;
- spawn calculation от `spawnPoints`, `spawnRules.playerSpawnRadius` и `bounds` карты;
- initial wind bootstrap от `defaultWind` карты;
- backward-compatible расширение `INIT_GAME_STATE` через `roomMeta`.

**Не включает (out of scope):**
- полноценное использование `colliders` для spawn avoidance;
- отдельный public endpoint со списком карт;
- clock-wise wind rotation policy следующих wave'ов.

## Технический подход
`RoomRegistry` теперь создаёт `GameRoom` не из `roomId` + period, а из `roomId`, display `roomName` и реального `MapTemplate`. Комната хранит template внутри runtime и использует его при старте: `Wind` инициализируется углом/скоростью из `defaultWind`, а `SpawnService` больше не опирается на hardcoded `(0,0)`/`[-30,30]`, а выбирает anchor из `spawnPoints` карты, применяет random offset в рамках `playerSpawnRadius` и валидирует координаты по `bounds` карты.

Для WS contract я сохранил старое поле `room` в `INIT_GAME_STATE`, чтобы не сломать текущий фронт, и добавил рядом новый `roomMeta` с `roomId`, `roomName`, `mapId`, `mapName`, `mapRevision`, `theme` и `bounds`. Это даёт фронту room/map metadata без разрыва обратной совместимости.

## Контракты и данные
### WebSocket
- `INIT_GAME_STATE` теперь содержит legacy `room` и новый `roomMeta`.
- `roomMeta` приходит из `MapTemplate`, а не из runtime hardcode.
- `SPAWN_ASSIGNED` остаётся тем же по shape, но его координаты теперь map-driven.

### Runtime
- `GameRoom` хранит `MapTemplate` внутри комнаты.
- `RoomRegistry` использует registry-resolved map template как источник bootstrap defaults.
- `Wind` умеет стартовать из произвольного `angle/speed`, заданных картой.

## Проверки
### Автоматические проверки
| Репозиторий | Команда | Зачем | Статус |
|-------------|---------|-------|--------|
| `sea_patrol_backend` | `\.\gradlew.bat test` | Проверить backend suite после map-driven bootstrap и contract change | `Passed` |

### Ручная проверка
- [x] Комната на `test-sandbox-01` отдаёт spawn возле map spawn point `(10, -10)` с углом `0.5`
- [x] `INIT_GAME_STATE.roomMeta` содержит `mapId/mapName/theme/bounds`
- [x] Initial wind для debug-карты стартует из `defaultWind = { angle: 1.57, speed: 4.0 }`

## QA / Review
### QA
- [x] Все acceptance criteria подтверждены
- [x] Backend test suite проходит полностью
- [x] Документация синхронизирована

**QA статус:** `Passed`

### Code Review
| Приоритет | Комментарий | Статус |
|-----------|-------------|--------|
| `Low` | Spawn policy уже map-driven, но пока ещё не использует `colliders` для исключения blocked positions; это остаётся отдельным follow-up для следующих wave'ов | `Open` |

**Review решение:** `Approve`

## Финализация
- [x] Runtime переведён на map-driven bootstrap
- [x] Tests проходят
- [x] Документация синхронизирована
- [x] Задача помечена как выполненная в roadmap
