# PROJECT_INFO.md

## Назначение
Документ описывает текущую архитектуру и структуру `sea_patrol_backend` как source of truth для инженерных задач.

## 1. Общее описание
- Тип проекта: backend для мультиплеерной морской игры.
- Архитектура: reactive (`Spring WebFlux` + `Project Reactor`), real-time через WebSocket.
- Функции:
  - регистрация и логин пользователей;
  - JWT-аутентификация для защищенных маршрутов и WebSocket;
  - lobby flow с room catalog, room creation и room join;
  - игровой цикл комнаты (физика, состояние игроков, ветер);
  - чат (server-scoped lobby chat, room chat, direct messages).

## 2. Технологический стек
- Язык: `Java 25` (toolchain в Gradle).
- Framework: `Spring Boot 4.0.3`.
- Web: `spring-boot-starter-webflux`.
- Security: `spring-boot-starter-security`, JWT на `jjwt 0.13.0`.
- Mapping: `MapStruct 1.6.3`.
- Utility: `Lombok`.
- Игровая физика: `LibGDX 1.12.1`, `Box2D`, `gdx-ai`.
- Build: `Gradle` (`build.gradle.kts`).

## 3. Структура репозитория
- `src/main/java/ru/sea/patrol/SeaPatrolApplication.java` — точка входа.
- `src/main/java/ru/sea/patrol/config` — безопасность и WebSocket-маршрутизация.
- `src/main/java/ru/sea/patrol/auth` — REST auth (`/api/v1/auth/*`) + JWT/security компоненты.
- `src/main/java/ru/sea/patrol/room` — REST room endpoints (`GET /api/v1/rooms`, `POST /api/v1/rooms`, `POST /api/v1/rooms/{roomId}/join`, `POST /api/v1/rooms/{roomId}/leave`).
- `src/main/java/ru/sea/patrol/user` — домен пользователей + in-memory репозиторий.
- `src/main/java/ru/sea/patrol/ws` — WebSocket handler `/ws/game` + протокол сообщений (MessageType + DTO).
- `src/main/java/ru/sea/patrol/service/chat` — чат-группы и сообщения.
- `src/main/java/ru/sea/patrol/service/game` — игровые комнаты, `RoomRegistry`, `RoomCatalogService`, `RoomJoinService`, `MapTemplateRegistry`, room config properties, цикл обновления, игроки.
- `src/main/java/ru/sea/patrol/service/session` — single-session policy и room/lobby binding для WS-пользователей.
- `src/main/java/ru/sea/patrol/error` — единый JSON-формат ошибок для приложенческих исключений.
- `src/main/resources/application.yaml` — конфиг приложения, JWT и room runtime defaults.
- `src/main/resources/worlds` — in-memory map packages (`manifest`, `colliders`, `spawn-points`, `poi`, `minimap` metadata), из которых `MapTemplateRegistry` собирает доступные карты.
- `src/main/resources/catalogs` — in-memory static catalogs (`ship-classes`, `items`, `merchants`, `quests`), которые `StaticCatalogRegistry` загружает из resource files без БД.
- `src/main/resources/static` — собранные фронтенд-артефакты.
- `src/test/java/ru/sea/patrol` — тесты (есть REST/WebSocket интеграционные и physics-тесты Box2D).

## 4. Runtime-потоки
### 4.1 HTTP / REST
- `POST /api/v1/auth/signup` создает пользователя в in-memory хранилище.
- `POST /api/v1/auth/login` валидирует учетные данные и возвращает JWT + timestamps.
- `GET /api/v1/rooms` возвращает текущий room catalog для lobby UI на основе `RoomRegistry`; пустые комнаты удаляются не мгновенно, а после отдельного `game.room.empty-room-idle-timeout`, когда в них уже нет активных игроков и не осталось room-bound reconnect grace.
- `POST /api/v1/rooms` создаёт новую комнату в `RoomRegistry`, а `mapId/mapName` валидируются и резолвятся через in-memory `MapTemplateRegistry`; после successful create lobby WS-клиенты получают `ROOMS_UPDATED`.
- `POST /api/v1/rooms/{roomId}/join` валидирует room admission и переводит текущую активную WS-сессию пользователя из lobby binding в room binding.
- `POST /api/v1/rooms/{roomId}/leave` делает обратный room-menu flow: удаляет игрока из runtime комнаты, переводит ту же активную WS-сессию обратно в `lobby`, переносит chat scope из `group:room:<roomId>` в `group:lobby`, отправляет `ROOMS_SNAPSHOT` и публикует `ROOMS_UPDATED`.
- Если у пользователя уже есть активная игровая WebSocket-сессия, повторный `login` отклоняется `401` с `SEAPATROL_DUPLICATE_SESSION`.

### 4.2 Безопасность
- Публичные маршруты: `/`, `/game`, статика, `POST /api/v1/auth/signup`, `POST /api/v1/auth/login`.
- Остальные HTTP-маршруты, включая `GET /api/v1/rooms`, `POST /api/v1/rooms`, `POST /api/v1/rooms/{roomId}/join` и `POST /api/v1/rooms/{roomId}/leave`, требуют JWT в `Authorization: Bearer <token>`.
- Для WebSocket handshake (`GET /ws/...`) токен читается из query-параметра `token`.

### 4.3 WebSocket / Игра
- Endpoint: `/ws/game`.
- При подключении пользователя:
  - `GameSessionRegistry` захватывает ownership active game session по `username`;
  - параллельное второе подключение для того же пользователя отклоняется `POLICY_VIOLATION`;
  - backend создаёт активную `lobby` session и автоматически добавляет пользователя в chat group `group:lobby`;
  - public chat scope определяется сервером по session binding: в `lobby` сообщения публикуются только в `group:lobby`, а после room join только в `group:room:<roomId>`;
  - клиентские `CHAT_JOIN` / `CHAT_LEAVE` не управляют lobby/room membership и игнорируются runtime-кодом;
  - игровой room stream не стартует автоматически и room binding ещё не назначен.
- Вход в игровую комнату выполняется через `POST /api/v1/rooms/{roomId}/join`:
  - backend проверяет наличие активной lobby session;
  - проверяет существование комнаты и лимит `maxPlayersPerRoom`;
  - подготавливает игрока к join, переключает session binding на `roomId` и переносит chat membership из `group:lobby` в `group:room:<roomId>`;
  - публикует `ROOMS_UPDATED` всем оставшимся lobby WS-клиентам как полный snapshot room catalog;
  - после успешного REST response по открытому WS отправляет `ROOM_JOINED`, затем `SPAWN_ASSIGNED`, затем `INIT_GAME_STATE` и дальнейшие room updates.
- Выход из игровой комнаты выполняется через `POST /api/v1/rooms/{roomId}/leave`:
  - backend проверяет существование комнаты и наличие активной room WS-session именно на тот же `roomId`;
  - убирает игрока из runtime комнаты без полного logout или разрыва всей auth/WS session;
  - переводит session binding обратно в `lobby` и переносит chat membership из `group:room:<roomId>` в `group:lobby`;
  - отправляет по той же WS-сессии `ROOMS_SNAPSHOT` как первый authoritative lobby snapshot после leave и затем публикует `ROOMS_UPDATED` для lobby клиентов.
- Частота обновлений комнаты задаётся через `game.room.update-period` (MVP default: `100ms`).
- Room wind rotation тоже конфигурируется на уровне backend через `game.room.wind-rotation-speed` (MVP default: `0.17453292 rad/s`, то есть примерно `10°/s`).
- После disconnect active session ownership снимается сразу: username перестаёт считаться active WS-session owner и может снова пройти login, а reconnect grace на `game.room.reconnect-grace-period` (MVP default: `15s`) удерживает room-bound runtime state для controlled resume.
- Если пользователь отключился из комнаты и после этого она осталась без других активных игроков, backend удерживает её в `RoomRegistry` до окончания reconnect grace; `currentPlayers` в lobby catalog не уменьшается мгновенно, потому что disconnected player остаётся частью retained room state до timeout.
- Reconnect в течение grace восстанавливает ту же room binding и текущего игрока в той же комнате: backend повторно шлёт `ROOM_JOINED` и `INIT_GAME_STATE`, но не делает новый spawn.
- Если grace истёк, backend выполняет final cleanup retained player/runtime state; после этого пустая комната остаётся в catalog с `currentPlayers = 0` ещё на отдельный `game.room.empty-room-idle-timeout`, а затем удаляется автоматически.
- Подготовительные room limits и reconnect defaults уже вынесены в `game.room.*`:
  - `max-rooms`;
  - `max-players-per-room`;
  - `reconnect-grace-period`;
  - `empty-room-idle-timeout`.

## 5. Доменные ограничения (текущее состояние)
- Пользователи и игровые сущности хранятся в памяти процесса.
- Предзаполненные пользователи в `InMemoryUserRepository`: `user1/user2/user3` с паролем `123456`.
- В auth DTO включена серверная валидация (`@Valid` + jakarta validation annotations) для `/api/v1/auth/signup` и `/api/v1/auth/login`.
- Нет версионирования WebSocket-протокола; изменения формата сообщений требуют ручной синхронизации клиента/сервера.
- `maxRooms`, `maxPlayersPerRoom` и room lifecycle уже конфигурируются через `game.room.*`, а `RoomRegistry` выступает единым source of truth для list/create/join/cleanup flows.
- `MapTemplateRegistry` уже загружает полный map package из `src/main/resources/worlds/*`: `manifest`, `colliders`, `spawn-points`, `poi`, `minimap` metadata и `defaultWind` settings.
- `StaticCatalogRegistry` теперь так же загружает `ship classes`, `item catalog`, `merchant catalog` и `quest definitions` из `src/main/resources/catalogs/*.json`; это in-memory source of truth для будущих cargo/trade/quest flows.
- Основные игровые flow по-прежнему не зависят от `Liquibase`/`H2`: пустой стенд поднимается только на resource files + in-memory registries.
- В текущем production bundle зарегистрированы две карты: default `caribbean-01` и dev/debug `test-sandbox-01`; внешний room REST/WS contract пока не меняется, но обе уже доступны через `mapId` validation в backend.
- Public chat routing для lobby/room теперь server-authoritative: legacy `to=global` переписывается в текущий scope пользователя, а попытки писать в чужую room group не проходят.
- `GameRoom` теперь хранит `MapTemplate` активной комнаты и поднимает runtime bootstrap из карты: initial wind стартует из `defaultWind`, а `INIT_GAME_STATE` включает `roomMeta` с `roomId`, `roomName`, `mapId`, `mapName`, `mapRevision`, `theme` и `bounds`.
- `GameRoom` также уже хранит room-local authoritative wind state: один и тот же `wind` snapshot включается в `INIT_GAME_STATE` как initial room state и затем приходит в каждом `UPDATE_GAME_STATE` для всех игроков комнаты.
- По состоянию на `TASK-035` room wind больше не меняется случайным шумом: backend вращает его по часовой стрелке с фиксированной room-wide скоростью, так что все игроки комнаты видят одинаковый предсказуемый drift направления.
- По состоянию на `TASK-033` ship movement на backend уже зависит от `wind`: `PlayerShipInstance` считает sail drive из силы ветра, относительного угла между курсом корабля и направлением ветра и затем применяет server-authoritative тягу в Box2D world.
- По состоянию на `TASK-033B` у игрока есть server-authoritative `sailLevel` (`0..3`, default `3`): `PLAYER_INPUT.up/down` меняют его по rising-edge, а итоговая тяга теперь зависит и от room `wind`, и от текущего уровня парусов.
- Во время reconnect grace `PlayerShipInstance.freeze()` останавливает drift retained ship state, поэтому room resume возвращает игрока в ту же комнату без нового spawn и без нежелательного смещения позиции на стороне backend.
- Initial spawn для room join вычисляется backend'ом из `spawnPoints` + `spawnRules.playerSpawnRadius` и валидируется по `MapTemplate.bounds`; тот же transport shape переиспользуется для server-side respawn path с `reason=RESPAWN`.
- `ROOM_JOIN_REJECTED` уже зарезервирован в WebSocket protocol surface, но текущий runtime ещё не отправляет это событие и использует REST error response как authoritative rejection channel.
- Reconnect grace уже участвует и в room resume, и в empty-room cleanup policy, но всё ещё зависит от in-memory runtime текущего backend-процесса.

## 6. Сборка и запуск
- Для запуска требуется JWT secret (одна из переменных окружения):
  - `JWT_SECRET` — raw строка (рекомендуется >= 32 байта);
  - `JWT_SECRET_BASE64` — base64/base64url-байты (после декодирования >= 32 байта).

- Room runtime defaults можно переопределить через env:
  - `GAME_DEFAULT_ROOM_NAME`
  - `GAME_ROOM_UPDATE_PERIOD`
  - `GAME_MAX_ROOMS`
  - `GAME_MAX_PLAYERS_PER_ROOM`
  - `GAME_RECONNECT_GRACE_PERIOD`
  - `GAME_EMPTY_ROOM_IDLE_TIMEOUT`
  - `GAME_WIND_ROTATION_SPEED`

- Windows:
  - `\.\gradlew.bat bootRun`
  - `\.\gradlew.bat test`
  - `\.\gradlew.bat build`
- Linux/macOS:
  - `./gradlew bootRun`
  - `./gradlew test`
  - `./gradlew build`

## 7. Риски и техдолг
- JWT secret не хранится в репозитории: задается через env `JWT_SECRET` (raw) или `JWT_SECRET_BASE64` (base64/base64url). Без секрета приложение не стартует.
- Physics-тесты Box2D/LibGDX используют native-библиотеки: возможны JVM warnings/особенности запуска на разных ОС/архитектурах.
- Статика фронтенда хранится как build output; ручные правки в `static/assets` легко приводят к рассинхронизации.
- Empty-room cleanup теперь предсказуем и bounded по `game.room.empty-room-idle-timeout`, но у комнат всё ещё нет owner/host policy или явного manual close flow.
- Manual room close/leave теперь есть только на уровне player exit-to-lobby (`POST /api/v1/rooms/{roomId}/leave`); owner/host semantics и удаление комнаты по инициативе владельца всё ещё не реализованы.
- Public chat routing для lobby/room теперь server-authoritative: legacy `to=global` переписывается в текущий scope пользователя, а попытки писать в чужую room group не проходят.
