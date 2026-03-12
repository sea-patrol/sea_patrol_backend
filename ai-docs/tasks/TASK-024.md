# TASK-024 - Backend часть: первая рабочая карта `caribbean-01`

## Метаданные
- **ID:** `TASK-024`
- **Тип:** `feature`
- **Статус:** `Done`
- **Приоритет:** `High`
- **Дата создания:** `2026-03-12`
- **Автор:** `Codex`
- **Связанный roadmap item:** `Wave 3 / TASK-024`
- **Трек:** `Backend`
- **Depends on:** `TASK-023`

## Контекст
После `TASK-023` backend уже умел держать in-memory `MapTemplateRegistry`, но в production runtime карта всё ещё была только тонким manifest entry без реального пакета дочерних world-файлов. Для следующих задач по room bootstrap, ветру и навигации нужна первая карта, которая проходит целостную validation и содержит базовые данные мира, а не только `id/name`.

## Цель
Сделать `caribbean-01` первой рабочей картой backend-MVP: подготовить минимальный package файлов (`manifest`, `colliders`, `spawn-points`, `poi`, `minimap` metadata, `defaultWind`) и расширить `MapTemplateRegistry`, чтобы он валидировал и загружал этот пакет целиком.

## Source of Truth
- Код:
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/map/MapTemplate.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/map/MapTemplateRegistry.java`
  - `sea_patrol_backend/src/main/resources/worlds/caribbean-01/manifest.json`
  - `sea_patrol_backend/src/main/resources/worlds/caribbean-01/colliders.json`
  - `sea_patrol_backend/src/main/resources/worlds/caribbean-01/spawn-points.json`
  - `sea_patrol_backend/src/main/resources/worlds/caribbean-01/poi.json`
  - `sea_patrol_backend/src/main/resources/worlds/caribbean-01/minimap.json`
- Тесты:
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/service/game/map/MapTemplateRegistryTest.java`
  - `sea_patrol_backend/src/test/resources/test-worlds/*`
  - backend regression suite (`RoomControllerTest`, `RoomJoinControllerTest`, `GameRoomCleanupPolicyTest`, `GameServiceSpawnAssignedTest`, `WsProtocolParsingTest`)
- Документация:
  - `sea_patrol_backend/ai-docs/API_INFO.md`
  - `sea_patrol_backend/ai-docs/PROJECT_INFO.md`
  - `sea_patrol_orchestration/API.md`
  - `sea_patrol_orchestration/PROJECTS_ORCESTRATION_INFO.md`
  - `sea_patrol_orchestration/ROADMAP-TASKS.md`

## Acceptance Criteria
- [x] Карта проходит validation.
- [x] Комнату можно создать с `mapId = caribbean-01`.
- [x] У карты есть enough metadata для лобби и room bootstrap.

## Scope
**Включает:**
- расширение `MapTemplate` до полноценного world template model;
- загрузку и validation linked files (`colliders`, `spawn-points`, `poi`, `minimap` metadata);
- `defaultWind` settings в manifest;
- production package для `caribbean-01`;
- test fixtures для valid/invalid map packages;
- docs sync и roadmap status.

**Не включает (out of scope):**
- подключение map metadata к фактическому room bootstrap runtime (`TASK-026`);
- вторую dev/debug карту `test-sandbox-01` в production bundle (`TASK-025`);
- frontend minimap rendering или room wind usage.

## Технический подход
`MapTemplateRegistry` теперь не ограничивается `id/name/defaultMap`, а читает manifest и все связанные с ним файлы через относительные пути из `files.*`. Валидный template должен иметь корректные `bounds`, `spawnRules`, `defaultWind`, хотя бы одну player spawn point, minimap calibration и parseable child JSON files. Если package битый, registry не публикует карту в available list.

Для production runtime добавлен полный минимальный package `worlds/caribbean-01/*`. Он ещё не используется как authoritative room bootstrap source в runtime-коде, но уже хранит все данные, которые понадобятся следующими задачами: `bounds/colliders`, `spawn-points`, `poi`, `minimap` metadata и `defaultWind`.

## Контракты и данные
### Map package layout
- `manifest.json`
- `colliders.json`
- `spawn-points.json`
- `poi.json`
- `minimap.json`

### Runtime notes
- Внешний REST/WS contract комнаты не меняется: room catalog и create/join responses всё ещё отдают `caribbean-01 / Caribbean Sea`.
- Разница в том, что теперь этот `mapId` указывает на полный backend-валидированный package, а не на тонкий placeholder manifest.

## Проверки
### Автоматические проверки
| Репозиторий | Команда | Зачем | Статус |
|-------------|---------|-------|--------|
| `sea_patrol_backend` | `\.\gradlew.bat test --tests ru.sea.patrol.service.game.map.MapTemplateRegistryTest` | Проверить загрузку полного map package и отбрасывание битой карты | `Passed` |
| `sea_patrol_backend` | `\.\gradlew.bat test` | Проверить отсутствие регрессий по backend suite | `Passed` |

### Ручная проверка
- [x] Production registry загружает полный package `caribbean-01`
- [x] В template доступны `bounds`, `spawn-points`, `poi`, `minimap` metadata и `defaultWind`
- [x] Битая test-карта с отсутствующим `spawn-points` не попадает в available maps

## QA / Review
### QA
- [x] Все acceptance criteria подтверждены
- [x] Backend test suite проходит полностью
- [x] Документация синхронизирована

**QA статус:** `Passed`

### Code Review
| Приоритет | Комментарий | Статус |
|-----------|-------------|--------|
| `Low` | Runtime пока ещё не использует map package как authoritative source для actual room bootstrap; это остаётся прямым продолжением в `TASK-026` | `Open` |

**Review решение:** `Approve`

## Финализация
- [x] Backend runtime и resources обновлены
- [x] Tests проходят
- [x] Документация синхронизирована
- [x] Задача помечена как выполненная в roadmap