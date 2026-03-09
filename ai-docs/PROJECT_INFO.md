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
- `src/main/java/ru/sea/patrol/room` — REST room endpoints (`GET /api/v1/rooms`, `POST /api/v1/rooms`, `POST /api/v1/rooms/{roomId}/join`).
- `src/main/java/ru/sea/patrol/user` — домен пользователей + in-memory репозиторий.
- `src/main/java/ru/sea/patrol/ws` — WebSocket handler `/ws/game` + протокол сообщений (MessageType + DTO).
- `src/main/java/ru/sea/patrol/service/chat` — чат-группы и сообщения.
- `src/main/java/ru/sea/patrol/service/game` — игровые комнаты, `RoomRegistry`, `RoomCatalogService`, `RoomJoinService`, room config properties, цикл обновления, игроки.
- `src/main/java/ru/sea/patrol/service/session` — single-session policy и room/lobby binding для WS-пользователей.
- `src/main/java/ru/sea/patrol/error` — единый JSON-формат ошибок для приложенческих исключений.
- `src/main/resources/application.yaml` — конфиг приложения, JWT и room runtime defaults.
- `src/main/resources/static` — собранные фронтенд-артефакты.
- `src/test/java/ru/sea/patrol` — тесты (есть REST/WebSocket интеграционные и physics-тесты Box2D).

## 4. Runtime-потоки
### 4.1 HTTP / REST
- `POST /api/v1/auth/signup` создает пользователя в in-memory хранилище.
- `POST /api/v1/auth/login` валидирует учетные данные и возвращает JWT + timestamps.
- `GET /api/v1/rooms` возвращает текущий room catalog для lobby UI на основе `RoomRegistry`; пустые комнаты удаляются после того, как в них не остаётся активных игроков и завершается room-bound reconnect grace.
- `POST /api/v1/rooms` создаёт новую комнату в `RoomRegistry` с room limits и минимальной map validation, после чего lobby WS-клиенты получают `ROOMS_UPDATED`.
- `POST /api/v1/rooms/{roomId}/join` валидирует room admission и переводит текущую активную WS-сессию пользователя из lobby binding в room binding.
- Если у пользователя уже есть активная игровая WebSocket-сессия, повторный `login` отклоняется `401` с `SEAPATROL_DUPLICATE_SESSION`.

### 4.2 Безопасность
- Публичные маршруты: `/`, `/game`, статика, `POST /api/v1/auth/signup`, `POST /api/v1/auth/login`.
- Остальные HTTP-маршруты, включая `GET /api/v1/rooms`, `POST /api/v1/rooms` и `POST /api/v1/rooms/{roomId}/join`, требуют JWT в `Authorization: Bearer <token>`.
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
- Частота обновлений комнаты задаётся через `game.room.update-period` (MVP default: `100ms`).
- После disconnect active session ownership снимается сразу: username перестаёт считаться active WS-session owner и может снова пройти login, а reconnect grace на `game.room.reconnect-grace-period` (MVP default: `15s`) удерживает room-bound runtime state для controlled resume.
- Если пользователь отключился из комнаты и после этого она осталась без других активных игроков, backend удерживает её в `RoomRegistry` до окончания reconnect grace; `currentPlayers` в lobby catalog не уменьшается мгновенно, потому что disconnected player остаётся частью retained room state до timeout.
- Reconnect в течение grace восстанавливает ту же room binding и текущего игрока в той же комнате: backend повторно шлёт `ROOM_JOINED` и `INIT_GAME_STATE`, но не делает новый spawn.
- Если grace истёк, backend выполняет final cleanup retained player/runtime state и только после этого удаляет пустую комнату из registry/catalog.
- Подготовительные room limits и reconnect defaults уже вынесены в `game.room.*`:
  - `max-rooms`;
  - `max-players-per-room`;
  - `reconnect-grace-period`.

## 5. Доменные ограничения (текущее состояние)
- Пользователи и игровые сущности хранятся в памяти процесса.
- Предзаполненные пользователи в `InMemoryUserRepository`: `user1/user2/user3` с паролем `123456`.
- В auth DTO включена серверная валидация (`@Valid` + jakarta validation annotations) для `/api/v1/auth/signup` и `/api/v1/auth/login`.
- Нет версионирования WebSocket-протокола; изменения формата сообщений требуют ручной синхронизации клиента/сервера.
- `maxRooms`, `maxPlayersPerRoom` и room lifecycle уже конфигурируются через `game.room.*`, а `RoomRegistry` выступает единым source of truth для list/create/join/cleanup flows.
- Room catalog, create room flow и room join flow пока используют временное default map metadata (`caribbean-01` / `Caribbean Sea`) до появления `MapTemplateRegistry`.
- Public chat routing для lobby/room теперь server-authoritative: legacy `to=global` переписывается в текущий scope пользователя, а попытки писать в чужую room group не проходят.
- Initial spawn для room join уже вычисляется только на backend как random offset вокруг `(0, 0)` и валидируется по MVP bounds `x/z in [-30.0, 30.0]`; тот же transport shape переиспользуется для server-side respawn path с `reason=RESPAWN`.
- `ROOM_JOIN_REJECTED` уже зарезервирован в WebSocket protocol surface, но текущий runtime ещё не отправляет это событие и использует REST error response как authoritative rejection channel.
- Reconnect grace уже участвует в empty-room cleanup policy и в повторном admission flow, но не держит пользователя в состоянии active session и не покрывает полный resume room state.

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

- Windows:
  - `.\gradlew.bat bootRun`
  - `.\gradlew.bat test`
  - `.\gradlew.bat build`
- Linux/macOS:
  - `./gradlew bootRun`
  - `./gradlew test`
  - `./gradlew build`

## 7. Риски и техдолг
- JWT secret не хранится в репозитории: задается через env `JWT_SECRET` (raw) или `JWT_SECRET_BASE64` (base64/base64url). Без секрета приложение не стартует.
- Physics-тесты Box2D/LibGDX используют native-библиотеки: возможны JVM warnings/особенности запуска на разных ОС/архитектурах.
- Статика фронтенда хранится как build output; ручные правки в `static/assets` легко приводят к рассинхронизации.
- Reconnect после disconnect сейчас решает только повторный admission той же учетной записи и временно удерживает пустую комнату на время grace; восстановление room membership/state еще не реализовано.
- Public chat routing для lobby/room теперь server-authoritative: legacy `to=global` переписывается в текущий scope пользователя, а попытки писать в чужую room group не проходят.








