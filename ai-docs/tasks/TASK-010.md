# TASK-010 - Backend часть: `POST /api/v1/rooms`

## Метаданные
- **ID:** `TASK-010`
- **Тип:** `feature`
- **Статус:** `Review`
- **Приоритет:** `High`
- **Дата создания:** `2026-03-07`
- **Автор:** `Codex`
- **Связанный roadmap item:** `Wave 1 / TASK-010`
- **Трек:** `Backend`
- **Depends on:** `TASK-008`, `TASK-009`

## Контекст
После `TASK-009` backend уже умел отдавать текущий room catalog, но не имел REST-команды для создания новых комнат из lobby UI. Для MVP следующая минимальная ступень - позволить фронту создавать комнату через защищённый `POST /api/v1/rooms`, соблюдая room limits и возвращая тот же summary shape, что и room catalog.

## Цель
Реализовать `POST /api/v1/rooms` с проверкой `maxRooms`, минимальной валидацией `mapId` и каноническим room summary response.

## Source of Truth
- Код:
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/room/api/RoomController.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/room/api/dto/RoomCreateRequestDto.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/RoomCatalogService.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/RoomRegistry.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/RoomRegistryEntry.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/error/domain/ConflictException.java`
- Тесты:
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/room/RoomControllerTest.java`
- Документация:
  - `sea_patrol_backend/ai-docs/API_INFO.md`
  - `sea_patrol_backend/ai-docs/PROJECT_INFO.md`
  - `sea_patrol_orchestration/API.md`

## Acceptance Criteria
- [x] Нельзя превысить `maxRooms`.
- [x] Комната создаётся с корректным `mapId`.
- [x] Ответ соответствует contract.

## Scope
**Включает:**
- защищённый `POST /api/v1/rooms`;
- room creation request DTO;
- `409 MAX_ROOMS_REACHED` для room limit enforcement;
- сохранение `id/name/mapId/mapName` в room registry entry;
- integration tests на success и error cases.

**Не включает (out of scope):**
- `POST /api/v1/rooms/{roomId}/join`;
- lobby WS `ROOMS_UPDATED`;
- полноценный `MapTemplateRegistry`.

## Технический подход
Создание комнаты реализовано через тот же `RoomCatalogService`, который уже отдаёт room snapshot. `RoomRegistry` теперь хранит не только runtime `GameRoom`, но и metadata entry (`id`, `name`, `mapId`, `mapName`), чтобы `POST /api/v1/rooms` и `GET /api/v1/rooms` использовали единый source of truth. Для room limits введён `ConflictException`, который мапится в `409` structured error.

## Контракты и данные
### Request shape
```json
{ "name": "Sandbox 3", "mapId": "caribbean-01" }
```

### Response shape
```json
{
  "id": "sandbox-3",
  "name": "Sandbox 3",
  "mapId": "caribbean-01",
  "mapName": "Caribbean Sea",
  "currentPlayers": 0,
  "maxPlayers": 100,
  "status": "OPEN"
}
```

### Правила текущей реализации
- endpoint защищён JWT;
- если `name` не передан, backend генерирует `sandbox-N` / `Sandbox N`;
- если `name` передан, `id` строится slugified-формой имени;
- пока backend принимает только `mapId = caribbean-01` или пустой `mapId`;
- при превышении лимита возвращается `409` + `MAX_ROOMS_REACHED`.

## Проверки
### Автоматические проверки
| Репозиторий | Команда | Зачем | Статус |
|-------------|---------|-------|--------|
| `sea_patrol_backend` | `.\gradlew.bat test` | Проверить room creation flow и отсутствие регресса по backend suite | `Passed` |

### Ручная проверка
- [x] Без токена `POST /api/v1/rooms` возвращает `401`
- [x] Пустой body `{}` создаёт `sandbox-1`
- [x] `INVALID_MAP_ID` возвращается как `400`
- [x] `MAX_ROOMS_REACHED` возвращается как `409`

## QA / Review
### QA
- [x] Все acceptance criteria подтверждены
- [x] Ключевые сценарии проходят
- [x] Регресс по backend test suite не обнаружен

**QA статус:** `Passed`

### Code Review
| Приоритет | Комментарий | Статус |
|-----------|-------------|--------|
| `Medium` | После появления `MapTemplateRegistry` нужно заменить временную single-map validation на реальный map catalog и убрать hardcoded `caribbean-01` | `Open` |

**Review решение:** `Approve`

## Финализация
- [x] Код backend обновлен
- [x] Tests проходят
- [ ] Задача перенесена в выполненные / архив
