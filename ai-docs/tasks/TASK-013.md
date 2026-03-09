# TASK-013 - Backend часть: lobby room updates через WS

## Метаданные
- **ID:** `TASK-013`
- **Тип:** `feature`
- **Статус:** `Review`
- **Приоритет:** `High`
- **Дата создания:** `2026-03-07`
- **Автор:** `Codex`
- **Связанный roadmap item:** `Wave 1 / TASK-013`
- **Трек:** `Backend`
- **Depends on:** `TASK-008`

## Контекст
После `TASK-009`, `TASK-010`, `TASK-011` и `TASK-012` backend уже умел отдавать room catalog по REST, создавать комнаты, выполнять room join и удерживать пустые комнаты на время reconnect grace. Но lobby still depended на повторных REST-запросах, потому что по WebSocket не было live-событий room catalog.

## Цель
Реализовать lobby room updates через WebSocket так, чтобы active lobby clients получали полный room snapshot без polling при `create`, `join`, `leave` и cleanup.

## Source of Truth
- Код:
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/RoomCatalogWsService.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/ws/game/GameWebSocketHandler.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/ws/protocol/MessageType.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/room/api/RoomController.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/RoomJoinService.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/GameService.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/session/GameSessionRegistry.java`
- Тесты:
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/room/RoomCatalogWsUpdatesTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/room/RoomJoinControllerTest.java`
- Документация:
  - `sea_patrol_backend/ai-docs/API_INFO.md`
  - `sea_patrol_backend/ai-docs/PROJECT_INFO.md`
  - `sea_patrol_orchestration/API.md`
  - `sea_patrol_orchestration/PROJECTS_ORCESTRATION_INFO.md`

## Acceptance Criteria
- [x] Lobby clients получают обновление без polling.
- [x] Payload соответствует contract.

## Scope
**Включает:**
- новые WS message types `ROOMS_SNAPSHOT`, `ROOMS_UPDATED`;
- автоматический `ROOMS_SNAPSHOT` при lobby WS-connect;
- `ROOMS_UPDATED` как full snapshot после `create`, `join`, `leave`, cleanup;
- фильтрацию room catalog stream только для active lobby sessions;
- integration tests на create/join/disconnect-cleanup updates.

**Не включает (out of scope):**
- frontend lobby screen;
- delta-патчи room catalog;
- client->server `ROOMS_LIST` request flow.

## Технический подход
Для lobby room updates добавлен отдельный singleton stream `RoomCatalogWsService`, который публикует `MessageOutput` с полным room snapshot. `GameWebSocketHandler` при lobby-подключении подмешивает в общий outbound stream сначала `ROOMS_SNAPSHOT`, а затем `ROOMS_UPDATED`, но только пока session binding остаётся в `lobby`.

Триггеры на публикацию расставлены в местах изменения room catalog state: `POST /api/v1/rooms`, `POST /api/v1/rooms/{roomId}/join`, disconnect/leave через `GameWebSocketHandler` + `GameService`, и final cleanup retained room через `GameSessionRegistry` после reconnect grace. Payload всегда берётся из `RoomCatalogService.getCatalog()`, то есть shape полностью совпадает с `GET /api/v1/rooms`.

## Контракты и данные
### `ROOMS_SNAPSHOT` / `ROOMS_UPDATED`
```json
{
  "maxRooms": 5,
  "maxPlayersPerRoom": 100,
  "rooms": [
    {
      "id": "sandbox-1",
      "name": "Sandbox 1",
      "mapId": "caribbean-01",
      "mapName": "Caribbean Sea",
      "currentPlayers": 0,
      "maxPlayers": 100,
      "status": "OPEN"
    }
  ]
}
```

### Правила текущей реализации
- `ROOMS_SNAPSHOT` отправляется автоматически при lobby WS-connect;
- `ROOMS_UPDATED` шлётся только lobby clients, room-bound sessions его больше не получают;
- payload всегда является полным snapshot, а не delta-патчем;
- отдельный `ROOMS_LIST` request со стороны клиента не нужен для текущего backend runtime.

## Проверки
### Автоматические проверки
| Репозиторий | Команда | Зачем | Статус |
|-------------|---------|-------|--------|
| `sea_patrol_backend` | `.\gradlew.bat test` | Проверить lobby WS room updates и отсутствие регресса по backend suite | `Passed` |

### Ручная проверка
- [x] При lobby WS-connect приходит `ROOMS_SNAPSHOT`
- [x] После `POST /api/v1/rooms` lobby client получает `ROOMS_UPDATED`
- [x] После `POST /api/v1/rooms/{roomId}/join` другой lobby client получает `ROOMS_UPDATED` с новым `currentPlayers`
- [x] После disconnect и последующего cleanup lobby client получает `ROOMS_UPDATED` без polling

## QA / Review
### QA
- [x] Все acceptance criteria подтверждены
- [x] WS payload совпадает с `GET /api/v1/rooms`
- [x] Регресс по backend test suite не обнаружен

**QA статус:** `Passed`

### Code Review
| Приоритет | Комментарий | Статус |
|-----------|-------------|--------|
| `Low` | Сейчас backend всегда шлёт полный snapshot. Если позже room catalog станет большим, возможно понадобится отдельная задача на diff/delta protocol, но это вне текущей MVP-каноники | `Open` |

**Review решение:** `Approve`

## Финализация
- [x] Код backend обновлен
- [x] Tests проходят
- [ ] Задача перенесена в выполненные / архив

