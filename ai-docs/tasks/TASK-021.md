# TASK-021 - Backend часть: reconnect grace room resume

## Метаданные
- **ID:** `TASK-021`
- **Тип:** `feature`
- **Статус:** `Done`
- **Приоритет:** `High`
- **Дата создания:** `2026-03-09`
- **Автор:** `Codex`
- **Связанный roadmap item:** `Wave 2 / TASK-021`
- **Трек:** `Backend`
- **Depends on:** `TASK-006`, `TASK-011`

## Контекст
До этой задачи backend уже умел держать single-session admission policy, room-bound reconnect grace и временно удерживать пустую комнату в registry. Но reconnect в пределах grace возвращал пользователя только в систему, а не в ту же комнату: active room binding сбрасывался в `lobby`, `Player` удалялся из `GameService` сразу на disconnect, а room catalog считал игрока ушедшим мгновенно.

## Цель
Сделать reconnect grace полноценным room resume flow для MVP: в пределах `15s` backend должен удерживать room-bound player/runtime state, позволять повторный WS handshake той же учетной записи и возвращать пользователя в ту же комнату без нового spawn.

## Source of Truth
- Код:
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/session/GameSessionRegistry.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/session/SessionGraceExpiredEvent.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/session/SessionGraceCleanupListener.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/GameService.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/GameRoom.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/Player.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/PlayerShipInstance.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/ws/game/GameWebSocketHandler.java`
  - `sea_patrol_backend/src/main/resources/application.yaml`
- Тесты:
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/service/session/GameSessionRegistryTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/game/GameRoomCleanupPolicyTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/room/RoomJoinControllerTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/room/RoomCatalogWsUpdatesTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/ws/WebSocketHandshakeTest.java`
- Документация:
  - `sea_patrol_backend/ai-docs/API_INFO.md`
  - `sea_patrol_backend/ai-docs/PROJECT_INFO.md`
  - `sea_patrol_orchestration/API.md`
  - `sea_patrol_orchestration/PROJECTS_ORCESTRATION_INFO.md`
  - `sea_patrol_orchestration/ROADMAP.md`
  - `sea_patrol_orchestration/ROADMAP-TASKS.md`
  - `sea_patrol_frontend/ai-docs/API_INFO.md`

## Acceptance Criteria
- [x] Session не удаляется мгновенно.
- [x] Reconnect within grace возвращает пользователя в ту же комнату.
- [x] После timeout retained session/runtime state удаляется.

## Scope
**Включает:**
- восстановление room binding из reconnect grace;
- удержание `Player` и room runtime state до истечения grace;
- resume flow через `ROOM_JOINED` + `INIT_GAME_STATE` без нового `SPAWN_ASSIGNED`;
- final cleanup retained player по timeout через event-driven lifecycle;
- снижение default `reconnect-grace-period` до `15s`;
- обновление backend integration/unit tests.

**Не включает (out of scope):**
- frontend reconnect UI;
- отдельный resume REST API;
- combat/death-specific respawn flow поверх reconnect.

## Технический подход
`GameSessionRegistry` теперь хранит и восстанавливает прежний `SessionBinding` при successful reconnect из grace. На timeout registry публикует `SessionGraceExpiredEvent`, а `SessionGraceCleanupListener` завершает final cleanup retained player/runtime state через `GameService`.

`GameWebSocketHandler` больше не удаляет room-bound `Player` сразу на disconnect: backend снимает active ownership, чистит chat membership, но оставляет room/player state живым до конца grace. При reconnect handler повторно переводит chat scope в room и запускает `resumeRoomSession`, который шлет `ROOM_JOINED` и свежий `INIT_GAME_STATE` без нового spawn.

Чтобы последовательные WS-сессии одного и того же игрока работали предсказуемо, `Player` получил resettable per-session sink и disconnect preparation logic: room subscription отписывается, ship freeze-ится на текущих координатах, а сама room membership не удаляется до timeout.

## Контракты и данные
### Session policy
- `game.room.reconnect-grace-period` default: `15s`.
- В пределах окна reconnect новый `login` и новый `/ws/game` handshake разрешены.
- Если disconnected user был привязан к комнате, reconnect восстанавливает ту же room binding.

### Resume flow
1. Старый WS disconnect переводит пользователя в `DISCONNECTED_GRACE`.
2. Новый `/ws/game` handshake в пределах grace проходит без duplicate-session reject.
3. Backend восстанавливает chat scope комнаты.
4. Backend повторно шлёт `ROOM_JOINED`.
5. Backend повторно шлёт актуальный `INIT_GAME_STATE`.
6. Новый `SPAWN_ASSIGNED` при reconnect не отправляется.

## Проверки
### Автоматические проверки
| Репозиторий | Команда | Зачем | Статус |
|-------------|---------|-------|--------|
| `sea_patrol_backend` | `.\gradlew.bat test` | Проверить reconnect resume, cleanup timeout и отсутствие регрессий по REST/WS | `Passed` |

### Ручная проверка
- [x] Закрытие вкладки больше не блокирует повторный `login`
- [x] Reconnect в пределах grace возвращает пользователя в ту же комнату
- [x] После истечения grace retained player удаляется и пустая комната очищается

## QA / Review
### QA
- [x] Все acceptance criteria подтверждены
- [x] Backend test suite проходит полностью
- [x] Контракты backend/orchestration/frontend docs синхронизированы

**QA статус:** `Passed`

### Code Review
| Приоритет | Комментарий | Статус |
|-----------|-------------|--------|
| `Low` | Frontend reconnect UX/state (`TASK-022`) все еще должен отдельно обработать `RECONNECTING`, timeout и возврат в lobby | `Open` |

**Review решение:** `Approve`

## Финализация
- [x] Backend runtime обновлен
- [x] Tests проходят
- [x] Документация синхронизирована
- [x] Задача помечена как выполненная в roadmap
