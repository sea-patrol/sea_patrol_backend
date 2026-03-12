# TASK-023 - Backend часть: MapTemplateRegistry как in-memory реестр карт

## Метаданные
- **ID:** `TASK-023`
- **Тип:** `feature`
- **Статус:** `Done`
- **Приоритет:** `High`
- **Дата создания:** `2026-03-10`
- **Автор:** `Codex`
- **Связанный roadmap item:** `Wave 3 / TASK-023`
- **Трек:** `Backend`
- **Depends on:** `TASK-010`

## Контекст
После `TASK-010` backend уже умел принимать `mapId` в room create flow, но фактически всё держалось на двух захардкоженных строках `caribbean-01 / Caribbean Sea`. Это мешало переходу к реальным world templates и делало карту неявной частью runtime. Нужен был отдельный in-memory registry, который грузит manifest-описания карт из ресурсов и становится source of truth для default map и `mapId` validation без базы данных.

## Цель
Ввести `MapTemplateRegistry`, который на старте backend загружает world manifests из `src/main/resources/worlds/*`, валидирует их, держит в памяти и отдаёт операции `list/get/default map`. Room catalog и create room flow должны опираться уже на него, а не на локальные константы.

## Source of Truth
- Код:
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/map/MapTemplate.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/map/MapTemplateRegistry.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/RoomCatalogService.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/RoomRegistry.java`
  - `sea_patrol_backend/src/main/resources/worlds/caribbean-01/manifest.json`
- Тесты:
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/service/game/map/MapTemplateRegistryTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/room/RoomControllerTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/room/RoomJoinControllerTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/game/GameRoomCleanupPolicyTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/game/GameServiceSpawnAssignedTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/ws/WsProtocolParsingTest.java`
  - `sea_patrol_backend/src/test/resources/test-worlds/*`
- Документация:
  - `sea_patrol_backend/ai-docs/API_INFO.md`
  - `sea_patrol_backend/ai-docs/PROJECT_INFO.md`
  - `sea_patrol_orchestration/API.md`
  - `sea_patrol_orchestration/PROJECTS_ORCESTRATION_INFO.md`
  - `sea_patrol_orchestration/ROADMAP-TASKS.md`
  - `sea_patrol_frontend/ai-docs/API_INFO.md`

## Acceptance Criteria
- [x] Registry умеет `list/get/default map`.
- [x] Битая карта не попадает в available maps.
- [x] Для работы registry не нужен `H2`.

## Scope
**Включает:**
- новый `MapTemplateRegistry` в backend;
- загрузку map manifests из `src/main/resources/worlds/*`;
- validation базовых полей карты (`id`, `name`, `defaultMap`);
- перевод room create/default map resolution на registry;
- unit/integration test coverage и sync docs.

**Не включает (out of scope):**
- полноценный пакет первой playable map (`bounds`, `spawn-points`, `POI`, `minimap`) из `TASK-024`;
- вторую техническую карту `test-sandbox-01` в production runtime (`TASK-025`);
- room bootstrap по map template metadata (`TASK-026`).

## Технический подход
Backend теперь держит отдельный `MapTemplateRegistry`, который при старте сканирует `classpath*:worlds/*/manifest.json`, пытается прочитать каждый manifest, отбрасывает невалидные ресурсы и строит in-memory индекс `mapId -> map template`. Registry требует ровно одну default map и отдаёт три операции: `list`, `get`, `defaultMap`.

`RoomCatalogService` больше не знает о захардкоженных `DEFAULT_MAP_ID/DEFAULT_MAP_NAME`: при `POST /api/v1/rooms` он берёт либо `defaultMap`, либо валидирует переданный `mapId` через registry. `RoomRegistry` тоже использует registry-derived default map для auto-created entries, чтобы room metadata больше не расходилась между runtime paths.

## Контракты и данные
### Runtime / configuration
- Registry работает полностью in-memory.
- Источник карт: `src/main/resources/worlds/*/manifest.json`.
- База данных для map registry не нужна.

### MVP map bundle
- В production bundle сейчас зарегистрирована только карта `caribbean-01`.
- Поэтому внешний REST/WS contract пока остаётся тем же: room catalog и create/join responses отдают `caribbean-01 / Caribbean Sea`.
- Любой другой `mapId` пока возвращает `400 INVALID_MAP_ID`.

## Проверки
### Автоматические проверки
| Репозиторий | Команда | Зачем | Статус |
|-------------|---------|-------|--------|
| `sea_patrol_backend` | `\.\gradlew.bat test` | Проверить загрузку registry, room create/list flows и отсутствие регрессий в backend suite | `Passed` |

### Ручная проверка
- [x] Production registry поднимается с `caribbean-01`
- [x] Невалидный test manifest не попадает в available maps
- [x] Room create/list flows продолжают отдавать тот же внешний contract

## QA / Review
### QA
- [x] Все acceptance criteria подтверждены
- [x] Backend test suite проходит полностью
- [x] Backend/orchestration/frontend docs синхронизированы

**QA статус:** `Passed`

### Code Review
| Приоритет | Комментарий | Статус |
|-----------|-------------|--------|
| `Low` | Registry сейчас хранит только базовый manifest (`id`, `name`, `defaultMap`); расширенные world assets и metadata остаются задачами `TASK-024` и `TASK-026` | `Open` |

**Review решение:** `Approve`

## Финализация
- [x] Backend runtime обновлен
- [x] Tests проходят
- [x] Документация синхронизирована
- [x] Задача помечена как выполненная в roadmap