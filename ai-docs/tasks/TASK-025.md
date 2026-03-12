# TASK-025 - Backend часть: техническая карта `test-sandbox-01`

## Метаданные
- **ID:** `TASK-025`
- **Тип:** `feature`
- **Статус:** `Done`
- **Приоритет:** `Medium`
- **Дата создания:** `2026-03-12`
- **Автор:** `Codex`
- **Связанный roadmap item:** `Wave 3 / TASK-025`
- **Трек:** `Backend`
- **Depends on:** `TASK-023`

## Контекст
После `TASK-024` production backend уже имел полноценный package карты `caribbean-01`, но для технических сценариев всё ещё не было отдельной dev/debug карты. Это мешало отделять «нормальную» MVP-карту от песочницы для проверки spawn, POI, ветра и будущих интерактивных/боевых сценариев.

## Цель
Добавить в production runtime техническую карту `test-sandbox-01`, чтобы backend мог валидировать и создавать комнаты не только на `caribbean-01`, но и на отдельном debug template с собственными `spawn-points`, `poi` и `defaultWind` metadata.

## Source of Truth
- Код / ресурсы:
  - `sea_patrol_backend/src/main/resources/worlds/test-sandbox-01/manifest.json`
  - `sea_patrol_backend/src/main/resources/worlds/test-sandbox-01/colliders.json`
  - `sea_patrol_backend/src/main/resources/worlds/test-sandbox-01/spawn-points.json`
  - `sea_patrol_backend/src/main/resources/worlds/test-sandbox-01/poi.json`
  - `sea_patrol_backend/src/main/resources/worlds/test-sandbox-01/minimap.json`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/map/MapTemplateRegistry.java`
- Тесты:
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/service/game/map/MapTemplateRegistryTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/room/RoomControllerTest.java`
- Документация:
  - `sea_patrol_backend/ai-docs/API_INFO.md`
  - `sea_patrol_backend/ai-docs/PROJECT_INFO.md`
  - `sea_patrol_orchestration/API.md`
  - `sea_patrol_orchestration/PROJECTS_ORCESTRATION_INFO.md`
  - `sea_patrol_orchestration/ROADMAP-TASKS.md`
  - `sea_patrol_frontend/ai-docs/API_INFO.md`

## Acceptance Criteria
- [x] Карта зарегистрирована в `MapTemplateRegistry`.
- [x] Её можно использовать для dev/debug комнат.

## Scope
**Включает:**
- production package карты `test-sandbox-01`;
- registry coverage на загрузку двух production карт;
- REST coverage на `POST /api/v1/rooms` с `mapId = test-sandbox-01`;
- docs sync по списку доступных `mapId`.

**Не включает (out of scope):**
- отдельный public endpoint для list available map templates;
- реальное переключение frontend UX на выбор карт по catalog API;
- использование map package как authoritative room bootstrap source (`TASK-026`).

## Технический подход
Я добавил `test-sandbox-01` в production `src/main/resources/worlds/*` с тем же package layout, что и у `caribbean-01`: `manifest`, `colliders`, `spawn-points`, `poi`, `minimap`. Карта остаётся enabled, но не default. Это позволяет `MapTemplateRegistry` публиковать её в available maps, а `RoomCatalogService` — честно принимать `mapId = test-sandbox-01` без специальных веток или hardcode.

В тестах закреплено два аспекта: registry теперь реально поднимает обе production-карты, а `POST /api/v1/rooms` умеет создавать комнату на `test-sandbox-01` и возвращает правильные `mapId/mapName` в summary response.

## Контракты и данные
### Доступные `mapId`
- `caribbean-01` — default MVP map.
- `test-sandbox-01` — dev/debug map.

### Runtime notes
- Внешний room REST/WS contract не меняется.
- Меняется только допустимое множество `mapId`: backend теперь принимает не одну, а две карты.

## Проверки
### Автоматические проверки
| Репозиторий | Команда | Зачем | Статус |
|-------------|---------|-------|--------|
| `sea_patrol_backend` | `\.\gradlew.bat test --tests ru.sea.patrol.service.game.map.MapTemplateRegistryTest --tests ru.sea.patrol.room.RoomControllerTest` | Проверить регистрацию второй production карты и room create flow с `test-sandbox-01` | `Passed` |
| `sea_patrol_backend` | `\.\gradlew.bat test` | Проверить отсутствие регрессий по backend suite | `Passed` |

### Ручная проверка
- [x] `MapTemplateRegistry` видит `caribbean-01` и `test-sandbox-01`
- [x] `POST /api/v1/rooms` принимает `mapId = test-sandbox-01`
- [x] Комната на debug-карте возвращает `mapName = Test Sandbox`

## QA / Review
### QA
- [x] Все acceptance criteria подтверждены
- [x] Backend test suite проходит полностью
- [x] Документация синхронизирована

**QA статус:** `Passed`

### Code Review
| Приоритет | Комментарий | Статус |
|-----------|-------------|--------|
| `Low` | Frontend всё ещё не получает отдельный map catalog endpoint, поэтому `test-sandbox-01` доступна через manual/custom `mapId`, а не через dedicated map selector UI | `Open` |

**Review решение:** `Approve`

## Финализация
- [x] Production resources обновлены
- [x] Tests проходят
- [x] Документация синхронизирована
- [x] Задача помечена как выполненная в roadmap