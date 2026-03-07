# TASK-008 - Backend часть: RoomRegistry как явный реестр активных комнат

## Метаданные
- **ID:** `TASK-008`
- **Тип:** `feature`
- **Статус:** `Review`
- **Приоритет:** `High`
- **Дата создания:** `2026-03-07`
- **Автор:** `Codex`
- **Связанный roadmap item:** `Wave 1 / TASK-008`
- **Трек:** `Backend`
- **Depends on:** `TASK-005`

## Контекст
После `TASK-005` backend уже имел typed room configuration, но lifecycle активных комнат всё ещё оставался неявным: `GameService` держал собственный `rooms` map и создавал комнаты через `computeIfAbsent` прямо в `startRoom` / `joinRoom`. Это делало room lifecycle размазанным по сервису игры и усложняло следующие backend задачи по каталогу комнат, join policy и cleanup.

Для `TASK-009`–`TASK-013` нужен отдельный source of truth для active rooms: не набор случайных операций над `Map`, а явная backend abstraction, через которую создаются, находятся и удаляются комнаты.

## Цель
Ввести `RoomRegistry` как отдельный backend layer для управления активными комнатами, убрать неявный `computeIfAbsent` flow из `GameService` и зафиксировать минимальный create/remove contract тестами.

## Source of Truth
- Код:
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/RoomRegistry.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/GameService.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/ws/game/GameWebSocketHandler.java`
- Тесты:
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/game/RoomRegistryTest.java`
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/ws/WsProtocolParsingTest.java`
- Внутренняя документация:
  - `sea_patrol_backend/ai-docs/PROJECT_INFO.md`
  - `sea_patrol_orchestration/ROADMAP.md`

## Acceptance Criteria
- [x] Комнаты создаются/получаются через один сервис.
- [x] Есть room registry abstraction.
- [x] Есть тест на создание и удаление комнаты.

## Scope
**Включает:**
- отдельный `RoomRegistry` для active room lifecycle;
- перевод `GameService` с собственного `rooms` map на registry;
- сохранение текущего поведения default room start/join path поверх нового registry;
- unit tests на create/remove contract реестра.

**Не включает (out of scope):**
- REST endpoints для rooms;
- room catalog payloads;
- admission validation (`FULL`, `NOT_FOUND`, `LOBBY_SESSION_REQUIRED`);
- room cleanup с учётом reconnect grace.

## Технический подход
Вместо скрытого room lifecycle внутри `GameService` backend теперь использует отдельный `RoomRegistry`, который отвечает за создание, поиск и удаление активных комнат. `GameService` остаётся orchestration layer для players/input, но больше не владеет `rooms` напрямую и не решает сам, как хранится registry.

Минимальный контракт registry намеренно узкий: `getOrCreateRoom`, `findRoom`, `removeRoomIfEmpty`, `roomCount`, `hasRoom`. Этого достаточно, чтобы следующие задачи строились поверх явной abstraction, но без premature design для будущего room catalog API.

## Изменения по репозиторию
### `sea_patrol_backend`
- [x] Добавить `RoomRegistry`
- [x] Перевести `GameService` на registry-backed room lifecycle
- [x] Обновить manual test wiring, где `GameService` создаётся напрямую
- [x] Добавить tests на create/remove contract registry
- [x] Обновить `ai-docs/PROJECT_INFO.md`
- [ ] Обновить `ai-docs/API_INFO.md` при изменении внешнего контракта

## Контракты и данные
### Room registry contract
- `getOrCreateRoom(roomName)` — возвращает существующую комнату или создаёт новую
- `findRoom(roomName)` — возвращает текущую активную комнату или `null`
- `removeRoomIfEmpty(roomName)` — удаляет комнату только если она пуста
- `roomCount()` / `hasRoom(roomName)` — технические методы для lifecycle/tests

### Что не меняется внешне
- `GameWebSocketHandler` всё ещё использует default room name из config
- backend по-прежнему автоматически помещает игрока в default room при `/ws/game` подключении
- внешний REST/WS contract не меняется

## Риски и меры контроля
| Риск | Почему это риск | Мера контроля |
|------|-----------------|---------------|
| Registry станет просто переименованным `Map` без lifecycle value | Тогда `TASK-008` не уменьшит сложность следующих задач | `GameService` больше не держит room map вообще; create/find/remove проходят только через `RoomRegistry` |
| Удаление пустой комнаты сломает текущий stop flow | `GameRoom.leave()` уже умеет останавливать комнату при последнем игроке | Добавлен test на create/remove contract; `removeRoomIfEmpty` работает только после проверки `isEmpty()` |
| Тесты с ручным созданием `GameService` сломаются из-за новой зависимости | `WsProtocolParsingTest` создаёт `GameService` вручную | Manual wiring обновлён под `RoomRegistry` |

## План реализации
1. Ввести `RoomRegistry` как отдельный сервис для active rooms.
2. Убрать `rooms` map и `computeIfAbsent` из `GameService`.
3. Подключить registry в существующий runtime flow без изменения внешнего поведения.
4. Добавить unit tests на create/delete lifecycle registry.
5. Синхронизировать backend project docs.

## Проверки
### Автоматические проверки
| Репозиторий | Команда | Зачем | Статус |
|-------------|---------|-------|--------|
| `sea_patrol_backend` | `.\gradlew.bat test` | Проверить compile/runtime и registry lifecycle contract | `Passed` |

### Ручная проверка
- [x] Проверено, что `GameService` больше не держит собственный `rooms` map
- [x] Проверено, что пустая комната удаляется из registry
- [x] Проверено, что повторный `getOrCreateRoom` возвращает тот же instance
- [x] Проверено, что existing WS parsing test wiring не сломано

## Реализация
### Измененные файлы
1. `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/RoomRegistry.java` - новый реестр активных комнат
2. `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/GameService.java` - room lifecycle переведён на registry
3. `sea_patrol_backend/src/test/java/ru/sea/patrol/game/RoomRegistryTest.java` - tests на create/remove contract
4. `sea_patrol_backend/src/test/java/ru/sea/patrol/ws/WsProtocolParsingTest.java` - manual wiring обновлён под новую зависимость
5. `sea_patrol_backend/ai-docs/PROJECT_INFO.md` - синхронизирована backend architecture documentation
6. `sea_patrol_backend/ai-docs/tasks/TASK-008.md` - backend task artifact

### Незапланированные находки
- Для `TASK-008` не понадобился большой redesign API `GameService`: достаточно было убрать владение room map и оставить сервис orchestration layer для players/input.
- Cleanup policy с учётом reconnect grace действительно стоит держать отдельно (`TASK-012`), иначе `RoomRegistry` быстро разрастётся лишними обязанностями.

## QA / Review
### QA
- [x] Все acceptance criteria подтверждены
- [x] Ключевые сценарии проходят
- [x] Регресс по backend test suite не обнаружен

**QA статус:** `Passed`

### Code Review
| Приоритет | Комментарий | Статус |
|-----------|-------------|--------|
| `Medium` | В `TASK-009`/`TASK-010` стоит решить, будет ли room catalog читать snapshot напрямую из `RoomRegistry` или через отдельный query service поверх него | `Open` |

**Review решение:** `Approve`

## Финализация
- [x] Код backend обновлен
- [x] Tests проходят
- [ ] Задача перенесена в выполненные / архив

## Ссылки
- Related docs: `sea_patrol_orchestration/ROADMAP.md`, `sea_patrol_backend/ai-docs/PROJECT_INFO.md`
