# TASK-012 - Backend часть: room cleanup policy

## Метаданные
- **ID:** `TASK-012`
- **Тип:** `feature`
- **Статус:** `Review`
- **Приоритет:** `High`
- **Дата создания:** `2026-03-07`
- **Автор:** `Codex`
- **Связанный roadmap item:** `Wave 1 / TASK-012`
- **Трек:** `Backend`
- **Depends on:** `TASK-008`

## Контекст
После появления `RoomRegistry` backend уже умел создавать и удалять пустые комнаты, но lifecycle ещё не учитывал reconnect grace. В результате после disconnect игрока комната удалялась сразу вместе с последним player entry, хотя roadmap требует сохранять её на время reconnect window и закрывать только если активные игроки так и не вернулись.

## Цель
Реализовать room cleanup policy, при которой пустая комната удаляется только тогда, когда в ней нет активных игроков и не осталось пользователей в room-bound reconnect grace.

## Source of Truth
- Код:
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/session/GameSessionRegistry.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/GameService.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/ws/game/GameWebSocketHandler.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/RoomRegistry.java`
- Тесты:
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/game/GameRoomCleanupPolicyTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/service/session/GameSessionRegistryTest.java`
- Документация:
  - `sea_patrol_backend/ai-docs/API_INFO.md`
  - `sea_patrol_backend/ai-docs/PROJECT_INFO.md`
  - `sea_patrol_orchestration/API.md`
  - `sea_patrol_orchestration/PROJECTS_ORCESTRATION_INFO.md`

## Acceptance Criteria
- [x] Пустая комната удаляется из registry.
- [x] Комната остаётся живой, пока есть игрок в 30-секундном grace.
- [x] Есть tests на cleanup behavior.

## Scope
**Включает:**
- удержание пустой комнаты на время room-bound reconnect grace;
- автоматическое удаление комнаты после завершения grace, если активные игроки не появились;
- cleanup retained room при reconnect пользователя в `lobby`, если grace закончился досрочно фактом reconnect;
- tests на immediate cleanup, delayed cleanup и retained-room release.

**Не включает (out of scope):**
- `ROOMS_UPDATED` broadcast в lobby;
- full reconnect room resume;
- восстановление игрока обратно в комнату после reconnect.

## Технический подход
`GameSessionRegistry` теперь знает не только single-session admission state, но и room binding для disconnected-grace сессий. При disconnect backend сначала переводит пользователя в `DISCONNECTED_GRACE`, а только потом чистит chat/game runtime state. `GameService.leaveRoom()` удаляет пустую комнату только если для этого `roomId` больше нет room-bound reconnect grace.

Когда grace истекает, `GameSessionRegistry` сам инициирует финальную проверку retained room через `RoomRegistry.removeRoomIfEmpty(roomId)`. Если пользователь успевает переподключиться в пределах окна, registry отменяет grace и тоже сразу триггерит cleanup retained room, потому что текущая каноника не поддерживает automatic room resume и reconnect возвращает пользователя в `lobby`.

## Правила текущей реализации
- пустая комната может временно оставаться в registry без активных игроков только из-за room-bound reconnect grace;
- `GET /api/v1/rooms` продолжает читать данные из `RoomRegistry`, поэтому удержанная пустая комната ещё видна в catalog до завершения grace;
- reconnect в течение grace не восстанавливает room membership и не отменяет cleanup room навсегда;
- после истечения grace или reconnect в `lobby` пустая retained room удаляется автоматически.

## Проверки
### Автоматические проверки
| Репозиторий | Команда | Зачем | Статус |
|-------------|---------|-------|--------|
| `sea_patrol_backend` | `.\gradlew.bat test` | Проверить cleanup policy, session lifecycle и отсутствие регресса по backend suite | `Passed` |

### Ручная проверка
- [x] Пустая комната без reconnect grace удаляется сразу после ухода последнего игрока
- [x] Пустая комната с room-bound reconnect grace остаётся в registry до истечения окна
- [x] После истечения grace retained room удаляется автоматически
- [x] Reconnect в `lobby` снимает удержание пустой комнаты и позволяет её удалить сразу

## QA / Review
### QA
- [x] Все acceptance criteria подтверждены
- [x] Cleanup behavior покрыт unit/integration-adjacent tests
- [x] Регресс по backend test suite не обнаружен

**QA статус:** `Passed`

### Code Review
| Приоритет | Комментарий | Статус |
|-----------|-------------|--------|
| `Medium` | Пока reconnect из grace возвращает пользователя только в `lobby`; если позже появится full room resume, cleanup policy придётся пересогласовать с восстановлением room binding | `Open` |

**Review решение:** `Approve`

## Финализация
- [x] Код backend обновлен
- [x] Tests проходят
- [ ] Задача перенесена в выполненные / архив

