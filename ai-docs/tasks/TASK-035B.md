# TASK-035B - Backend часть: room leave flow без logout

## Метаданные
- **ID:** `TASK-035B`
- **Тип:** `feature`
- **Статус:** `Done`
- **Приоритет:** `High`
- **Дата создания:** `2026-03-13`
- **Автор:** `Codex`
- **Связанный roadmap item:** `Wave 4.5 / TASK-035B`
- **Трек:** `Backend`
- **Depends on:** `TASK-035A`, `TASK-021`

## Контекст
После `TASK-035A` leave-room flow был уже зафиксирован на уровне orchestration, но backend runtime всё ещё не умел вернуть игрока из комнаты обратно в lobby без полного disconnect/logout. Это блокировало menu-driven `Exit to lobby` на фронте.

## Цель
Реализовать backend-authoritative `POST /api/v1/rooms/{roomId}/leave`, который удаляет игрока из runtime комнаты, rebinding'ит ту же WS-сессию обратно в `lobby`, переводит chat scope назад в `group:lobby` и возвращает lobby catalog без relogin.

## Source of Truth
- Код:
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/room/api/RoomController.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/room/api/dto/RoomLeaveResponseDto.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/RoomLeaveService.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/session/GameSessionRegistry.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/chat/ChatService.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/GameService.java`
- Тесты:
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/room/RoomJoinControllerTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/service/session/GameSessionRegistryTest.java`
- Документация:
  - `sea_patrol_backend/ai-docs/API_INFO.md`
  - `sea_patrol_backend/ai-docs/PROJECT_INFO.md`
  - `sea_patrol_frontend/ai-docs/API_INFO.md`
  - `sea_patrol_frontend/ai-docs/PROJECT_INFO.md`
  - `sea_patrol_orchestration/API.md`
  - `sea_patrol_orchestration/PROJECTS_ORCESTRATION_INFO.md`
  - `sea_patrol_orchestration/ROADMAP-TASKS.md`

## Acceptance Criteria
- [x] Backend реализует `POST /api/v1/rooms/{roomId}/leave`.
- [x] Игрок после leave не теряет auth и текущую WS-сессию, а возвращается в `lobby`.
- [x] Chat scope переводится из `group:room:<roomId>` обратно в `group:lobby`.
- [x] Leaving player получает `ROOMS_SNAPSHOT`, а lobby catalog обновляется без регрессии по room counters/cleanup.
- [x] Есть tests на `ROOM_SESSION_REQUIRED`, `ROOM_SESSION_MISMATCH` и successful `room -> lobby` transition.

## Scope
**Включает:**
- новый защищённый REST endpoint `POST /api/v1/rooms/{roomId}/leave`;
- отдельный backend service для leave-room flow;
- safe rebinding active room session обратно в `lobby`;
- перевод chat membership назад в `group:lobby`;
- integration/unit tests и синхронизацию docs/roadmap.

**Не включает (out of scope):**
- frontend menu modal и кнопку `Выйти`;
- отдельный `ROOM_LEFT` WS event;
- owner/host policy и manual close room.

## Технический подход
- `RoomLeaveService` зеркалит join flow, но в обратную сторону: валидирует room existence, проверяет active room binding именно на тот `roomId`, переводит session в `lobby`, удаляет игрока из runtime комнаты и переносит chat scope назад в lobby.
- Для session layer добавлен enum-результат `LobbyRebindResult`, чтобы различать `ROOM_SESSION_REQUIRED` и `ROOM_SESSION_MISMATCH` без гонок и без размытых boolean-кодов.
- После успешного leave backend явно отвечает leaving player сообщением `ROOMS_SNAPSHOT`, а затем публикует `ROOMS_UPDATED` для lobby listeners.
- `GameService.leaveRoom(...)` переиспользован как authoritative runtime cleanup path без запуска reconnect grace.

## Контракты и данные
### REST
- `POST /api/v1/rooms/{roomId}/leave`
- response:
```json
{
  "roomId": "sandbox-1",
  "status": "LEFT",
  "nextState": "LOBBY"
}
```

### Ошибки
- `ROOM_NOT_FOUND`
- `ROOM_SESSION_REQUIRED`
- `ROOM_SESSION_MISMATCH`

### WS-семантика
- тот же WebSocket connect сохраняется;
- после успешного REST leave backend rebinding'ит session в `lobby` и шлёт `ROOMS_SNAPSHOT`;
- отдельный `ROOM_LEFT` message type не вводится.

## Проверки
### Автоматические проверки
| Репозиторий | Команда | Зачем | Статус |
|-------------|---------|-------|--------|
| `sea_patrol_backend` | `.\gradlew.bat test` | Проверить новый leave-room flow, session rebinding и отсутствие регрессий в backend suite | `Passed` |

### Ручная проверка
- [x] Leave из комнаты возвращает пользователя в lobby snapshot без relogin
- [x] Попытка leave из lobby даёт `ROOM_SESSION_REQUIRED`
- [x] Попытка leave чужого `roomId` даёт `ROOM_SESSION_MISMATCH`

## QA / Review
### QA
- [x] Все acceptance criteria подтверждены
- [x] Backend test suite проходит полностью
- [x] Документация синхронизирована

**QA статус:** `Passed`

### Code Review
| Приоритет | Комментарий | Статус |
|-----------|-------------|--------|
| `Low` | Leaving player после successful leave получает и явный `ROOMS_SNAPSHOT`, и следом общий `ROOMS_UPDATED`; это допустимо для MVP, но frontend должен считать snapshot authoritative и быть tolerant к повторному каталогу | `Resolved` |

**Review решение:** `Approve`

## Финализация
- [x] Backend runtime обновлён
- [x] Tests проходят
- [x] Документация синхронизирована
- [x] Задача помечена как выполненная в roadmap
