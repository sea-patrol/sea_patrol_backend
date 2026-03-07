# TASK-009 - Backend часть: `GET /api/v1/rooms`

## Метаданные
- **ID:** `TASK-009`
- **Тип:** `feature`
- **Статус:** `Review`
- **Приоритет:** `High`
- **Дата создания:** `2026-03-07`
- **Автор:** `Codex`
- **Связанный roadmap item:** `Wave 1 / TASK-009`
- **Трек:** `Backend`
- **Depends on:** `TASK-008`

## Контекст
После `TASK-008` backend уже получил явный `RoomRegistry`, но frontend lobby всё ещё не имел REST snapshot endpoint для первичной загрузки списка комнат. Контракт для `GET /api/v1/rooms` уже был зафиксирован в orchestration docs, поэтому задача `TASK-009` сводится к тому, чтобы отдать этот room catalog из runtime-состояния backend без polling-зависимости от WebSocket.

## Цель
Реализовать защищённый `GET /api/v1/rooms`, который возвращает текущий snapshot активных комнат с `roomId`, заполненностью и минимальной map metadata.

## Source of Truth
- Код:
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/room/api/RoomController.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/RoomCatalogService.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/RoomRegistry.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/GameRoom.java`
- Тесты:
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/room/RoomControllerTest.java`
- Документация:
  - `sea_patrol_backend/ai-docs/API_INFO.md`
  - `sea_patrol_backend/ai-docs/PROJECT_INFO.md`
  - `sea_patrol_orchestration/API.md`

## Acceptance Criteria
- [x] REST endpoint возвращает список комнат.
- [x] Пустой каталог и заполненный каталог корректно обрабатываются.
- [x] Есть integration tests.

## Scope
**Включает:**
- новый защищённый endpoint `GET /api/v1/rooms`;
- room catalog DTO для lobby snapshot;
- query service поверх `RoomRegistry`;
- integration tests на `401`, empty catalog и non-empty catalog.

**Не включает (out of scope):**
- `POST /api/v1/rooms`;
- `POST /api/v1/rooms/{roomId}/join`;
- WS `ROOMS_SNAPSHOT` / `ROOMS_UPDATED`;
- реальный `MapTemplateRegistry`.

## Технический подход
`RoomController` отдаёт snapshot через отдельный `RoomCatalogService`, а не читает runtime registry напрямую. `RoomRegistry` получил только read-side snapshot method, а `GameRoom` - безопасный счётчик игроков для room summary. До отдельной задачи по картам backend использует временные `mapId/mapName` defaults (`caribbean-01` / `Caribbean Sea`), чтобы уже сейчас соответствовать зафиксированному room contract.

## Изменения по репозиторию
### `sea_patrol_backend`
- [x] Добавить `GET /api/v1/rooms`
- [x] Добавить room catalog DTO/query service
- [x] Читать active rooms из `RoomRegistry`
- [x] Добавить integration tests на empty/non-empty/unauthorized cases
- [x] Синхронизировать `ai-docs/API_INFO.md`
- [x] Синхронизировать `ai-docs/PROJECT_INFO.md`

## Контракты и данные
### Response shape
```json
{
  "maxRooms": 5,
  "maxPlayersPerRoom": 100,
  "rooms": [
    {
      "id": "main",
      "name": "main",
      "mapId": "caribbean-01",
      "mapName": "Caribbean Sea",
      "currentPlayers": 1,
      "maxPlayers": 100,
      "status": "OPEN"
    }
  ]
}
```

### Правила текущей реализации
- endpoint защищён JWT;
- `rooms` может быть пустым массивом;
- `status` вычисляется как `OPEN | FULL` на основе `currentPlayers` и `maxPlayersPerRoom`;
- до `TASK-025` map metadata остаётся временным default placeholder.

## Проверки
### Автоматические проверки
| Репозиторий | Команда | Зачем | Статус |
|-------------|---------|-------|--------|
| `sea_patrol_backend` | `.\gradlew.bat test` | Проверить room REST endpoint и отсутствие регресса по backend suite | `Passed` |

### Ручная проверка
- [x] Без токена endpoint возвращает `401` + structured error
- [x] Пустой `RoomRegistry` даёт пустой `rooms[]`
- [x] Активная комната попадает в snapshot с корректной заполненностью

## QA / Review
### QA
- [x] Все acceptance criteria подтверждены
- [x] Ключевые сценарии проходят
- [x] Регресс по backend test suite не обнаружен

**QA статус:** `Passed`

### Code Review
| Приоритет | Комментарий | Статус |
|-----------|-------------|--------|
| `Medium` | В `TASK-010` стоит заменить временное `mapId/mapName` на реальные данные комнаты или map registry, чтобы room catalog не оставался placeholder-only | `Open` |

**Review решение:** `Approve`

## Финализация
- [x] Код backend обновлен
- [x] Tests проходят
- [ ] Задача перенесена в выполненные / архив
