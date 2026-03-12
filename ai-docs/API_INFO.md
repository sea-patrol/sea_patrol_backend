# API_INFO.md

## Назначение
Документ фиксирует текущее API backend (REST + WebSocket) по фактической реализации кода.

## 1. Базовая информация
- Base URL (локально): `http://localhost:8080` (дефолт Spring Boot).
- API-префикс auth: `/api/v1/auth`.
- API-префикс rooms: `/api/v1/rooms`.
- WebSocket endpoint: `/ws/game`.
- Формат данных: JSON.

## 2. Аутентификация
### 2.1 HTTP
- Для защищенных HTTP-эндпоинтов используется заголовок:
  - `Authorization: Bearer <jwt>`

### 2.2 WebSocket handshake
- Для `GET /ws/...` токен ожидается в query-параметре:
  - `ws://localhost:8080/ws/game?token=<jwt>`
- Сервис сам добавляет префикс `Bearer ` внутри converter.

## 3. REST API
### 3.1 `POST /api/v1/auth/signup`
Создает пользователя (in-memory).

Request body:
```json
{
  "username": "new_user",
  "password": "123456",
  "email": "user@example.com"
}
```

Response `200 OK`:
```json
{
  "username": "new_user"
}
```

Примечания:
- Пароль хешируется `BCrypt`.
- Роль проставляется как `USER`.
- Признак `locked=false`.
- DTO валидируется через `@Valid` и jakarta validation annotations.
- Сейчас нет проверки уникальности username: in-memory repository использует `username` как ключ и при повторном signup перезаписывает запись.

### 3.2 `POST /api/v1/auth/login`
Аутентифицирует пользователя и возвращает JWT.

Request body:
```json
{
  "username": "user1",
  "password": "123456"
}
```

Response `200 OK`:
```json
{
  "username": "user1",
  "token": "<jwt>",
  "issuedAt": "2026-03-04T11:00:00.000+00:00",
  "expiresAt": "2026-03-04T12:00:00.000+00:00"
}
```

Примечания:
- `userId` не входит в текущий backend response contract.
- `username`, `token`, `issuedAt`, `expiresAt` покрыты integration tests.

Ошибки логина (через `AuthException`):
- `SEAPATROL_INVALID_USERNAME`
- `SEAPATROL_INVALID_PASSWORD`
- `SEAPATROL_USER_ACCOUNT_DISABLED`
- `SEAPATROL_DUPLICATE_SESSION` - если у пользователя уже есть активная игровая WebSocket-сессия

### 3.3 `GET /`
Возвращает `static/index.html` (`text/html`).

### 3.4 `GET /game`
Возвращает `static/index.html` (`text/html`).

### 3.5 `GET /api/v1/rooms`
Возвращает текущий room catalog для lobby UI.

Требует заголовок:
- `Authorization: Bearer <jwt>`

Response `200 OK`:
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

Примечания:
- `rooms` может быть пустым массивом;
- список комнат берётся из `RoomRegistry`;
- комнаты сортируются по `id`;
- `mapId` и `mapName` теперь резолвятся через in-memory `MapTemplateRegistry`, который валидирует полный map package из `src/main/resources/worlds/*`;
- в текущем production bundle зарегистрированы `caribbean-01` и `test-sandbox-01`; первая остаётся default-картой MVP, вторая доступна как dev/debug room template;
- пустая комната удаляется из registry не сразу: сначала в ней не должно остаться активных игроков и room-bound reconnect grace, после чего backend ждёт отдельный `game.room.empty-room-idle-timeout` (MVP default: `30s`).

### 3.6 `POST /api/v1/rooms`
Создаёт новую комнату для lobby flow.

Требует заголовок:
- `Authorization: Bearer <jwt>`

Request body (все поля опциональны):
```json
{ "name": "Sandbox 3", "mapId": "caribbean-01" }
```

Response `201 Created`:
```json
{
  "id": "sandbox-3",
  "name": "Sandbox 3",
  "mapId": "caribbean-01",
  "mapName": "Caribbean Sea",
  "currentPlayers": 0,
  "maxPlayers": 100,
  "status": "OPEN"
}
```

Примечания:
- если `name` не передан, backend генерирует следующий `sandbox-N` и display name `Sandbox N`;
- если `name` передан, `id` строится slugified-формой имени, а `name` сохраняется как display label;
- backend валидирует `mapId` против своего in-memory `MapTemplateRegistry`; сейчас доступны `caribbean-01` и `test-sandbox-01`, а остальные значения возвращают `INVALID_MAP_ID`;
- `test-sandbox-01` предназначена для dev/debug комнат и уже содержит отдельные `spawn-points`, `poi` и `defaultWind` metadata.
- если лимит `maxRooms` достигнут, backend возвращает `409` + `MAX_ROOMS_REACHED`;
- после успешного создания backend публикует `ROOMS_UPDATED` active lobby WebSocket-клиентам;
- если в созданную комнату никто не зайдёт, она автоматически исчезнет из catalog после `game.room.empty-room-idle-timeout`.

Ошибки:
- `400` -> `{ "errors": [{ "code": "INVALID_MAP_ID", "message": "Unknown mapId" }] }`
- `409` -> `{ "errors": [{ "code": "MAX_ROOMS_REACHED", "message": "Maximum number of rooms reached" }] }`

### 3.7 `POST /api/v1/rooms/{roomId}/join`
Подтверждает вход игрока в комнату и переключает активную WS-сессию из `lobby` в потоки комнаты.

Требует заголовок:
- `Authorization: Bearer <jwt>`

Request body:
```json
{}
```

Response `200 OK`:
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

Примечания:
- join невозможен без активной lobby WebSocket session для того же `username`;
- backend проверяет существование комнаты и лимит `maxPlayersPerRoom`;
- после success backend переводит chat binding из `group:lobby` в `group:room:<roomId>`;
- после room admission backend публикует `ROOMS_UPDATED` всем active lobby WebSocket-клиентам;
- после REST `200 OK` backend отправляет по активному WS последовательность `ROOM_JOINED` -> `SPAWN_ASSIGNED` -> `INIT_GAME_STATE`;
- initial spawn вычисляется только на backend из `MapTemplate`: anchor берётся из `spawnPoints`, random offset ограничивается `spawnRules.playerSpawnRadius`, а итоговые координаты валидируются по `bounds` карты;
- `INIT_GAME_STATE` теперь дополнительно включает `roomMeta`, собранный из room runtime и `MapTemplate` (`roomId`, `roomName`, `mapId`, `mapName`, `mapRevision`, `theme`, `bounds`);
- backend также держит отдельный respawn emission path с тем же payload shape и `reason=RESPAWN` для active room player.

Ошибки:
- `404` -> `{ "errors": [{ "code": "ROOM_NOT_FOUND", "message": "Room not found" }] }`
- `409` -> `{ "errors": [{ "code": "ROOM_FULL", "message": "Room is full" }] }`
- `409` -> `{ "errors": [{ "code": "LOBBY_SESSION_REQUIRED", "message": "Active lobby WebSocket session is required" }] }`

## 4. WebSocket API (`/ws/game`)
### 4.1 Транспортный формат и session policy
### Session policy
- Backend допускает только одну активную игровую WebSocket-сессию на `username`.
- Повторное параллельное подключение с тем же пользователем отклоняется закрытием `POLICY_VIOLATION` с reason, содержащим `SEAPATROL_DUPLICATE_SESSION`.
- После disconnect active session ownership снимается сразу, а username переводится в reconnect grace на `game.room.reconnect-grace-period` (MVP default: `15s`); в этот интервал новый login и новое WS-подключение разрешаются.
- Если disconnect произошёл из игровой комнаты, backend удерживает room binding и player runtime state до истечения grace; lobby room catalog не уменьшает `currentPlayers` мгновенно и обновляется после final cleanup retained player, а затем отдельным событием после room idle-timeout.
- Reconnect в течение grace восстанавливает ту же room binding без повторного `POST /api/v1/rooms/{roomId}/join`: backend повторно шлёт `ROOM_JOINED`, затем `INIT_GAME_STATE`, не эмитит новый `SPAWN_ASSIGNED` и возвращает игрока в ту же комнату.
- Если grace истёк, backend удаляет retained player из room runtime state; если после этого комната стала пустой, она остаётся в catalog с `currentPlayers = 0` до `game.room.empty-room-idle-timeout`, а следующий WS handshake стартует как новая `lobby` session.
- После успешного WS handshake backend создаёт активную `lobby` session для пользователя и автоматически добавляет его в chat group `group:lobby`.
- Public chat scope для `lobby` / `room` управляется только сервером по session binding; клиентские `CHAT_JOIN` / `CHAT_LEAVE` не могут подписать пользователя на чужую room group.
- При lobby WebSocket-подключении backend автоматически отправляет `ROOMS_SNAPSHOT` с текущим room catalog.
- До явного REST `POST /api/v1/rooms/{roomId}/join` пользователь не привязан к игровой комнате и не получает room stream.
- Все live-изменения каталога (`create`, `join`, `leave`, cleanup`) публикуются как `ROOMS_UPDATED` полным snapshot payload без delta-патчей; empty-room cleanup может приходить в два шага: сначала room остаётся с `currentPlayers = 0`, потом исчезает после idle-timeout.

### Входящие сообщения от клиента
Сервер ожидает массив:
```json
["MESSAGE_TYPE", { "..." : "..." }]
```
или для join/leave чата payload может быть строкой.

Пример:
```json
["PLAYER_INPUT", {"left": false, "right": true, "up": true, "down": false}]
```

### Исходящие сообщения от сервера
Сервер отправляет объект:
```json
{
  "type": "MESSAGE_TYPE",
  "payload": { "...": "..." }
}
```

## 4.1.1 Версионирование протокола (рекомендация)
Сейчас явного версионирования нет. Если понадобится breaking change, рекомендуется согласовать один из вариантов:
- добавить опциональное поле `protocolVersion` в исходящий envelope (сервер -> клиент) и/или входящие сообщения клиента;
- или ввести необязательное приветственное сообщение типа `HELLO`/`CAPABILITIES` с версиями/фичами.

## 4.2 Поддерживаемые типы сообщений
Enum `MessageType`:
- `CHAT_MESSAGE`
- `CHAT_JOIN` (legacy compatibility; public group membership runtime-кодом игнорируется)
- `CHAT_LEAVE` (legacy compatibility; public group membership runtime-кодом игнорируется)
- `ROOMS_SNAPSHOT`
- `ROOMS_UPDATED`
- `PLAYER_INPUT`
- `PLAYER_JOIN`
- `PLAYER_LEAVE`
- `ROOM_JOINED`
- `ROOM_JOIN_REJECTED`
- `SPAWN_ASSIGNED`
- `INIT_GAME_STATE`
- `UPDATE_GAME_STATE`

## 4.3 Сообщения клиента -> сервер
### `CHAT_MESSAGE`
Payload:
```json
{
  "from": "ignored_by_server",
  "to": "group:lobby | group:room:<roomId> | global (legacy) | user:<username>",
  "text": "message text"
}
```
Поведение:
- `from` переписывается сервером текущим username.
- `to=user:*` — в личный канал адресата + копия отправителю.
- Любой public chat target (`group:lobby`, `group:room:*`, `global`) сервер переписывает в фактический scope активной сессии пользователя.
- Если у пользователя активна `lobby` session, public message публикуется только в `group:lobby`.
- Если у пользователя active room binding, public message публикуется только в `group:room:<roomId>`.
- `to=global` остаётся только как legacy alias для текущего public scope на время перехода фронта.
- Попытка отправить сообщение в чужую room group не позволяет обойти room chat isolation.

### `CHAT_JOIN`
Payload: строка с именем группы, например:
```json
["CHAT_JOIN", "group:party-1"]
```

Примечание:
- тип остаётся в protocol surface для обратной совместимости;
- backend runtime игнорирует client-managed membership changes для `group:lobby` / `group:room:*`.

### `CHAT_LEAVE`
Payload: строка с именем группы, например:
```json
["CHAT_LEAVE", "group:party-1"]
```

Примечание:
- тип остаётся в protocol surface для обратной совместимости;
- backend runtime игнорирует client-managed membership changes для `group:lobby` / `group:room:*`.

### `PLAYER_INPUT`
Payload:
```json
{
  "left": true,
  "right": false,
  "up": true,
  "down": false
}
```

## 4.4 Сообщения сервер -> клиент
### `CHAT_MESSAGE`
Payload (`ChatMessage`):
```json
{
  "from": "username",
  "to": "group:lobby|group:room:<roomId>|user:*",
  "text": "..."
}
```
Примечание:
- для public chat backend возвращает уже разрешённый `to` (`group:lobby` или `group:room:<roomId>`), а не доверяет произвольному target из клиента.


### `ROOMS_SNAPSHOT`
Payload совпадает с `GET /api/v1/rooms`:
```json
{
  "maxRooms": 5,
  "maxPlayersPerRoom": 100,
  "rooms": []
}
```

Примечание:
- отправляется автоматически при lobby WebSocket-подключении.

### `ROOMS_UPDATED`
Payload совпадает с `GET /api/v1/rooms`:
```json
{
  "maxRooms": 5,
  "maxPlayersPerRoom": 100,
  "rooms": []
}
```

Примечания:
- публикуется для active lobby WebSocket-клиентов после `create`, `join`, `leave`, cleanup;
- при final leave/grace expiry room catalog может сначала показать комнату с `currentPlayers = 0`, а затем отдельным `ROOMS_UPDATED` удалить её после idle-timeout.
- payload всегда является полным snapshot, а не delta-патчем.

### `ROOM_JOINED`
Payload:
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

### `ROOM_JOIN_REJECTED`
Payload (shape зарезервирован в протоколе):
```json
{
  "roomId": "sandbox-1",
  "reason": "FULL"
}
```

Примечание:
- тип уже есть в backend enum как часть согласованного room protocol surface;
- в текущей реализации `TASK-011` отказ по join возвращается через REST error response, а отдельное WS-событие `ROOM_JOIN_REJECTED` ещё не отправляется runtime-кодом.

### `SPAWN_ASSIGNED`
Payload:
```json
{
  "roomId": "sandbox-1",
  "reason": "INITIAL",
  "x": 12.5,
  "z": -8.0,
  "angle": 0.0
}
```

Примечания:
- spawn/respawn остаётся server-authoritative;
- initial spawn для current runtime вычисляется backend'ом из `MapTemplate.spawnPoints` и `spawnRules.playerSpawnRadius`;
- итоговые координаты обязаны попадать в `MapTemplate.bounds` активной комнаты;
- `INIT_GAME_STATE` для текущего игрока должен совпадать с координатами из последнего `SPAWN_ASSIGNED`;
- текущий runtime уже эмитит `INITIAL` в room join flow и умеет эмитить `RESPAWN` через отдельный backend respawn path; death/combat trigger остаётся задачей следующих wave'ов.

### `PLAYER_JOIN`
Payload (`PlayerInfo`):
```json
{
  "name": "user1",
  "health": 500,
  "maxHealth": 500,
  "velocity": 0.0,
  "x": 12.5,
  "z": -8.0,
  "angle": 0.0,
  "model": "model",
  "height": 4.0,
  "width": 7.0,
  "length": 26.0
}
```

### `PLAYER_LEAVE`
Payload: имя игрока (строка).

### `INIT_GAME_STATE`
Payload:
```json
{
  "room": "sandbox-1",
  "roomMeta": {
    "roomId": "sandbox-1",
    "roomName": "Sandbox 1",
    "mapId": "caribbean-01",
    "mapName": "Caribbean Sea",
    "mapRevision": 1,
    "theme": "tropical",
    "bounds": {
      "minX": -5000.0,
      "maxX": 5000.0,
      "minZ": -5000.0,
      "maxZ": 5000.0
    }
  },
  "wind": {"angle": 0.0, "speed": 10.0},
  "players": [
    {
      "name": "user1",
      "health": 500,
      "maxHealth": 500,
      "velocity": 0.0,
      "x": 12.5,
      "z": -8.0,
      "angle": 0.5,
      "model": "model",
      "height": 4.0,
      "width": 7.0,
      "length": 26.0
    }
  ]
}
```

Семантика `wind`:
- `angle` приходит в радианах в плоскости `XZ`;
- `0` означает направление вдоль `+X`, `PI / 2` — вдоль `+Z`;
- backend runtime строит направление ветра как `Vector2(cos(angle), sin(angle))`, поэтому frontend и тесты должны интерпретировать угол именно так;
- `speed` — неотрицательная скалярная сила ветра;
- `INIT_GAME_STATE.wind` — initial authoritative snapshot room wind.

### `UPDATE_GAME_STATE`
Payload:
```json
{
  "delta": 0.1,
  "wind": {"angle": 0.0, "speed": 10.0},
  "players": [
    {
      "name": "user1",
      "health": 500,
      "velocity": 0.0,
      "x": 12.5,
      "z": -8.0,
      "angle": 0.0
    }
  ]
}
```

Семантика `UPDATE_GAME_STATE.wind`:
- payload shape совпадает с `INIT_GAME_STATE.wind`;
- это полный текущий authoritative snapshot ветра комнаты, а не delta-патч;
- до `TASK-035` backend не обещает фиксированную clockwise policy изменения направления, поэтому клиент должен просто применять последнее значение без локальных предположений о вращении.

Примечание (backpressure): сервер может пропускать часть сообщений `UPDATE_GAME_STATE` для медленных клиентов (best-effort). Клиент должен быть готов не получать каждое обновление и просто применять последнее полученное состояние.

## 5. Формат ошибок
Для приложенческих ошибок (через глобальный handler) ответ:
```json
{
  "errors": [
    {
      "code": "SEAPATROL_INVALID_PASSWORD",
      "message": "Invalid password"
    }
  ]
}
```

Статусы:
- `401` для auth/unauthorized/JWT ошибок.
- `400` для `ApiException`.
- `400` для validation ошибок (`SEAPATROL_VALIDATION_ERROR`).
- `404` для `NotFoundException`.
- `409` для `ConflictException`.
- `500` для прочих ошибок.

Важно:
- Ошибки, обработанные напрямую security entry point/access denied handler, возвращают JSON в том же формате (например, `SEAPATROL_UNAUTHORIZED` / `SEAPATROL_FORBIDDEN`).
- `SEAPATROL_DUPLICATE_SESSION` возвращается как `401` на `POST /api/v1/auth/login`, если у пользователя уже есть активная игровая WebSocket-сессия.

## 6. CORS (текущее значение)
Разрешенные origins:
- `http://localhost:5173`
- `http://localhost:4173`











