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
- до `TASK-025` backend отдаёт временное default map metadata: `mapId=caribbean-01`, `mapName=Caribbean Sea`;
- пустая комната удаляется из registry, когда в ней больше нет активных игроков и не осталось игроков в reconnect grace для этого `roomId`.

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
- пока backend принимает только `mapId=caribbean-01` или пустой `mapId`;
- если лимит `maxRooms` достигнут, backend возвращает `409` + `MAX_ROOMS_REACHED`.

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
- после REST `200 OK` backend отправляет по активному WS последовательность `ROOM_JOINED` -> `SPAWN_ASSIGNED` -> `INIT_GAME_STATE`;
- текущий `SPAWN_ASSIGNED` использует временный authoritative placeholder spawn `(x=0.0, z=0.0, angle=0.0)` до отдельных задач по spawn logic.

Ошибки:
- `404` -> `{ "errors": [{ "code": "ROOM_NOT_FOUND", "message": "Room not found" }] }`
- `409` -> `{ "errors": [{ "code": "ROOM_FULL", "message": "Room is full" }] }`
- `409` -> `{ "errors": [{ "code": "LOBBY_SESSION_REQUIRED", "message": "Active lobby WebSocket session is required" }] }`

## 4. WebSocket API (`/ws/game`)
### 4.1 Транспортный формат и session policy
### Session policy
- Backend допускает только одну активную игровую WebSocket-сессию на `username`.
- Повторное параллельное подключение с тем же пользователем отклоняется закрытием `POLICY_VIOLATION` с reason, содержащим `SEAPATROL_DUPLICATE_SESSION`.
- После disconnect username переходит в reconnect grace на `game.room.reconnect-grace-period`; в этот интервал новое WS-подключение разрешается.
- Если disconnect произошёл из игровой комнаты, пустая комната сохраняется в registry на время reconnect grace и удаляется после истечения окна, если активные игроки так и не появились.
- Reconnect в течение grace только повторно допускает пользователя в систему и возвращает его в `lobby`; полный resume room state не входит в текущий контракт и будет отдельной задачей.
- После успешного WS handshake backend создаёт активную `lobby` session для пользователя и автоматически добавляет его в chat group `group:lobby`.
- До явного REST `POST /api/v1/rooms/{roomId}/join` пользователь не привязан к игровой комнате и не получает room stream.

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
- `CHAT_JOIN`
- `CHAT_LEAVE`
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
  "to": "global | group:<id> | user:<username>",
  "text": "message text"
}
```
Поведение:
- `from` переписывается сервером текущим username.
- `to=global` — в глобальный чат.
- `to=group:*` — в указанную группу.
- `to=user:*` — в личный канал адресата + копия отправителю.

### `CHAT_JOIN`
Payload: строка с именем группы, например:
```json
["CHAT_JOIN", "group:party-1"]
```

### `CHAT_LEAVE`
Payload: строка с именем группы, например:
```json
["CHAT_LEAVE", "group:party-1"]
```

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
  "from": "system|username",
  "to": "global|group:*|user:*",
  "text": "..."
}
```

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
  "x": 0.0,
  "z": 0.0,
  "angle": 0.0
}
```

Примечания:
- spawn/respawn остаётся server-authoritative;
- для текущего backend runtime initial join отправляет placeholder coordinates `(0.0, 0.0, 0.0)`;
- отдельная spawn logic и non-placeholder assignment остаются следующими backend задачами.

### `PLAYER_JOIN`
Payload (`PlayerInfo`):
```json
{
  "name": "user1",
  "health": 500,
  "maxHealth": 500,
  "velocity": 0.0,
  "x": 0.0,
  "z": 0.0,
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
  "wind": {"angle": 0.0, "speed": 0.0},
  "players": [
    {
      "name": "user1",
      "health": 500,
      "maxHealth": 500,
      "velocity": 0.0,
      "x": 0.0,
      "z": 0.0,
      "angle": 0.0,
      "model": "model",
      "height": 4.0,
      "width": 7.0,
      "length": 26.0
    }
  ]
}
```

### `UPDATE_GAME_STATE`
Payload:
```json
{
  "delta": 0.1,
  "wind": {"angle": 0.0, "speed": 0.0},
  "players": [
    {
      "name": "user1",
      "health": 500,
      "velocity": 0.0,
      "x": 0.0,
      "z": 0.0,
      "angle": 0.0
    }
  ]
}
```

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


