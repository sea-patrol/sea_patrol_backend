# TASK-011 - Backend часть: `POST /api/v1/rooms/{roomId}/join`

## Метаданные
- **ID:** `TASK-011`
- **Тип:** `feature`
- **Статус:** `Review`
- **Приоритет:** `High`
- **Дата создания:** `2026-03-07`
- **Автор:** `Codex`
- **Связанный roadmap item:** `Wave 1 / TASK-011`
- **Трек:** `Backend`
- **Depends on:** `TASK-008`, `TASK-006`

## Контекст
После `TASK-009` и `TASK-010` backend уже умел отдавать room catalog и создавать комнаты, но не имел канонического admission flow для входа игрока в комнату. Для MVP следующий обязательный шаг - реализовать защищённый `POST /api/v1/rooms/{roomId}/join`, который использует уже открытую lobby WebSocket session и переводит пользователя в room streams без альтернативного WS-only flow.

## Цель
Реализовать `POST /api/v1/rooms/{roomId}/join` с валидацией lobby session, room existence и room capacity, а также с переключением активной WS-сессии из `lobby` в `room`.

## Source of Truth
- Код:
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/room/api/RoomController.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/RoomJoinService.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/GameService.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/session/GameSessionRegistry.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/chat/ChatService.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/ws/game/GameWebSocketHandler.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/ws/protocol/MessageType.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/ws/protocol/dto/RoomJoinResponseDto.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/ws/protocol/dto/SpawnAssignedResponseDto.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/error/domain/NotFoundException.java`
- Тесты:
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/room/RoomJoinControllerTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/service/session/GameSessionRegistryTest.java`
- Документация:
  - `sea_patrol_backend/ai-docs/API_INFO.md`
  - `sea_patrol_backend/ai-docs/PROJECT_INFO.md`
  - `sea_patrol_orchestration/API.md`

## Acceptance Criteria
- [x] Join невозможен без активной lobby WS session.
- [x] `FULL` и `NOT_FOUND` cases возвращаются корректно.
- [x] После success backend переключает session binding на room.

## Scope
**Включает:**
- защищённый `POST /api/v1/rooms/{roomId}/join`;
- backend validation для `LOBBY_SESSION_REQUIRED`, `ROOM_NOT_FOUND`, `ROOM_FULL`;
- lobby-first WebSocket model без auto-join в default room;
- переключение chat membership `group:lobby -> group:room:<roomId>`;
- WS sequence `ROOM_JOINED -> SPAWN_ASSIGNED -> INIT_GAME_STATE` после успешного REST join;
- integration tests на success/error cases и single-session binding.

**Не включает (out of scope):**
- `ROOMS_UPDATED` broadcast в lobby;
- empty-room cleanup policy;
- полноценную spawn logic и non-placeholder spawn assignment;
- full reconnect room resume.

## Технический подход
`GameWebSocketHandler` больше не помещает пользователя в default room при открытии `/ws/game`: WebSocket стартует в состоянии `lobby`, а `GameSessionRegistry` хранит lobby binding как исходную точку. `RoomJoinService` проверяет наличие активной lobby session, ищет `RoomRegistryEntry`, валидирует capacity, подготавливает room join через `GameService`, затем атомарно переключает binding в `GameSessionRegistry` и переносит пользователя из `group:lobby` в `group:room:<roomId>` через `ChatService`.

Для канонического room-init flow backend после REST `200 OK` отправляет по уже открытому WS `ROOM_JOINED`, затем `SPAWN_ASSIGNED`, затем активирует room subscription и `INIT_GAME_STATE`. Текущий `SPAWN_ASSIGNED` использует placeholder authoritative payload `(0.0, 0.0, 0.0)` до следующих задач по spawn calculation.

## Контракты и данные
### Request shape
```json
{}
```

### Response shape
```json
{
  "roomId": "sandbox-1",
  "mapId": "caribbean-01",
  "mapName": "Caribbean Sea",
  "currentPlayers": 1,
  "maxPlayers": 100,
  "status": "JOINED"
}
```

### Error shape
```json
{
  "errors": [
    {
      "code": "ROOM_FULL",
      "message": "Room is full"
    }
  ]
}
```

### Правила текущей реализации
- endpoint защищён JWT;
- join требует уже открытую lobby WS session для того же пользователя;
- backend не поддерживает WS-only join flow;
- current room chat начинает использовать `group:room:<roomId>` только после успешного REST join;
- `ROOM_JOIN_REJECTED` уже добавлен в enum протокола, но runtime сейчас использует REST errors как authoritative rejection channel.

## Проверки
### Автоматические проверки
| Репозиторий | Команда | Зачем | Статус |
|-------------|---------|-------|--------|
| `sea_patrol_backend` | `.\gradlew.bat test` | Проверить room join flow, session binding и отсутствие регресса по backend suite | `Passed` |

### Ручная проверка
- [x] Без активной lobby WS session `POST /api/v1/rooms/{roomId}/join` возвращает `409 LOBBY_SESSION_REQUIRED`
- [x] Для отсутствующей комнаты backend возвращает `404 ROOM_NOT_FOUND`
- [x] Для заполненной комнаты backend возвращает `409 ROOM_FULL`
- [x] При успехе REST response возвращает `JOINED`, а по WS приходят `ROOM_JOINED` и `SPAWN_ASSIGNED`

## QA / Review
### QA
- [x] Все acceptance criteria подтверждены
- [x] Ключевые success/error сценарии покрыты integration tests
- [x] Регресс по backend test suite не обнаружен

**QA статус:** `Passed`

### Code Review
| Приоритет | Комментарий | Статус |
|-----------|-------------|--------|
| `Medium` | `SPAWN_ASSIGNED` пока использует placeholder coordinates `(0.0, 0.0, 0.0)`; после задач по spawn logic нужно заменить это на реальный authoritative spawn selection | `Open` |
| `Low` | `ROOM_JOIN_REJECTED` уже зарезервирован в protocol surface, но runtime пока не эмитит его отдельно; это допустимо для текущего MVP, но при переходе к lobby live-sync поведение нужно будет довести до конца | `Open` |

**Review решение:** `Approve`

## Финализация
- [x] Код backend обновлен
- [x] Tests проходят
- [ ] Задача перенесена в выполненные / архив

