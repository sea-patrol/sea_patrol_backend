# API_INFO.md

## Назначение
Документ фиксирует текущее API backend (REST + WebSocket) по фактической реализации кода.

## 1. Базовая информация
- Base URL (локально): `http://localhost:8080` (дефолт Spring Boot).
- API-префикс auth: `/api/v1/auth`.
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

## 4. WebSocket API (`/ws/game`)
## 4.1 Транспортный формат
### Session policy
- Backend допускает только одну активную игровую WebSocket-сессию на `username`.
- Повторное параллельное подключение с тем же пользователем отклоняется закрытием `POLICY_VIOLATION` с reason, содержащим `SEAPATROL_DUPLICATE_SESSION`.
- После disconnect username переходит в reconnect grace на `game.room.reconnect-grace-period`; в этот интервал новое WS-подключение разрешается.
- Текущая реализация разрешает reconnect только на уровне session admission. Полный resume room state не входит в текущий контракт и будет отдельной задачей.

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
  "room": "main",
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
- `500` для прочих ошибок.

Важно:
- Ошибки, обработанные напрямую security entry point/access denied handler, возвращают JSON в том же формате (например, `SEAPATROL_UNAUTHORIZED` / `SEAPATROL_FORBIDDEN`).
- `SEAPATROL_DUPLICATE_SESSION` возвращается как `401` на `POST /api/v1/auth/login`, если у пользователя уже есть активная игровая WebSocket-сессия.

## 6. CORS (текущее значение)
Разрешенные origins:
- `http://localhost:5173`
- `http://localhost:4173`





