# TASK-017A - Backend часть: server-authoritative chat isolation для lobby и rooms

## Метаданные
- **ID:** `TASK-017A`
- **Тип:** `feature`
- **Статус:** `Review`
- **Приоритет:** `High`
- **Дата создания:** `2026-03-07`
- **Автор:** `Codex`
- **Связанный roadmap item:** `Wave 1 / TASK-017A`
- **Трек:** `Backend`
- **Depends on:** `TASK-011`, `TASK-013`

## Контекст
После `TASK-011` и `TASK-013` backend уже умел переводить пользователя из `group:lobby` в `group:room:<roomId>`, но фактическая изоляция чатов всё ещё держалась на дисциплине клиента. Runtime принимал `to=global`, позволял отправлять сообщения в произвольные `group:*` и не запрещал клиентские `CHAT_JOIN` / `CHAT_LEAVE`, из-за чего room chat isolation не был server-authoritative.

## Цель
Сделать public chat scope полностью серверным: lobby users общаются только внутри `group:lobby`, room users только внутри `group:room:<roomId>`, а попытки писать или подписываться на чужие комнаты не дают утечек между scope'ами.

## Source of Truth
- Код:
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/chat/ChatService.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/session/GameSessionRegistry.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/ws/game/GameWebSocketHandler.java`
- Тесты:
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/chat/ChatServiceTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/room/RoomChatIsolationWsTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/room/RoomCatalogWsUpdatesTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/service/session/GameSessionRegistryTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/ws/WsProtocolParsingTest.java`
- Документация:
  - `sea_patrol_backend/ai-docs/API_INFO.md`
  - `sea_patrol_backend/ai-docs/PROJECT_INFO.md`
  - `sea_patrol_frontend/ai-docs/API_INFO.md`
  - `sea_patrol_orchestration/API.md`
  - `sea_patrol_orchestration/PROJECTS_ORCESTRATION_INFO.md`

## Acceptance Criteria
- [x] Игрок в `lobby` получает только lobby chat.
- [x] Игрок в комнате получает только chat своей комнаты.
- [x] Сообщения между разными комнатами не протекают.
- [x] Есть backend tests на lobby chat и room chat isolation.

## Scope
**Включает:**
- server-authoritative routing public chat сообщений по session binding;
- отказ от runtime-зависимости от `global` как реального общего чата;
- игнорирование client-managed `CHAT_JOIN` / `CHAT_LEAVE` для `group:lobby` и `group:room:*`;
- integration tests на `lobby -> room` isolation и `room A -> room B` isolation;
- синхронизацию backend/orchestration docs под новую модель.

**Не включает (out of scope):**
- frontend refactor chat UI (`TASK-017B`);
- новую визуальную lobby page (`TASK-017C`);
- удаление `CHAT_JOIN` / `CHAT_LEAVE` из enum протокола как breaking change.

## Технический подход
`ChatService` больше не доверяет public `to` из клиента как authoritative routing key. Для любых public сообщений backend смотрит на active session binding в `GameSessionRegistry`: пока пользователь в `lobby`, public chat всегда уходит в `group:lobby`; после успешного room join — только в `group:room:<roomId>`. Это закрывает сценарии вроде `to=global` или `to=group:room:<otherRoomId>`.

Direct messages `to=user:*` сохранены без изменения. `CHAT_JOIN` / `CHAT_LEAVE` оставлены в protocol surface ради обратной совместимости, но runtime игнорирует client-managed membership changes для lobby/room channels, чтобы клиент больше не мог самовольно подписаться на чужой room stream.

## Контракты и данные
### `CHAT_MESSAGE` client -> server
```json
{
  "to": "group:lobby | group:room:<roomId> | global (legacy) | user:<username>",
  "text": "message text"
}
```

### Правила текущей реализации
- `to=user:*` остаётся direct message channel;
- любой public target сервер переписывает в фактический active scope пользователя;
- `to=global` поддерживается только как переходный alias до фронтового `TASK-017B`;
- outbound `CHAT_MESSAGE.payload.to` всегда содержит уже разрешённый server scope.

## Проверки
### Автоматические проверки
| Репозиторий | Команда | Зачем | Статус |
|-------------|---------|-------|--------|
| `sea_patrol_backend` | `.\\gradlew.bat test` | Проверить scoped chat routing и отсутствие регресса по backend suite | `Passed` |

### Ручная проверка
- [x] Lobby user не получает room chat после `POST /api/v1/rooms/{roomId}/join` другого пользователя
- [x] Room user не получает lobby chat после перехода в room scope
- [x] Пользователь из room A не получает сообщения room B, даже если отправитель пытается указать `to=group:room:<otherRoomId>`

## QA / Review
### QA
- [x] Все acceptance criteria подтверждены
- [x] Есть unit и integration coverage на scoped chat behavior
- [x] Регресс по backend test suite не обнаружен

**QA статус:** `Passed`

### Code Review
| Приоритет | Комментарий | Статус |
|-----------|-------------|--------|
| `Low` | `CHAT_JOIN` / `CHAT_LEAVE` всё ещё остаются в enum и docs для compatibility. Если позже понадобится чистый protocol surface, это надо делать отдельной breaking-change задачей вместе с фронтом | `Open` |
| `Low` | Frontend runtime всё ещё может отправлять legacy `to=global`; backend это уже безопасно нормализует, но визуальная явность chat scope остаётся задачей `TASK-017B` | `Open` |

**Review решение:** `Approve`

## Финализация
- [x] Код backend обновлен
- [x] Tests проходят
- [ ] Задача перенесена в выполненные / архив

