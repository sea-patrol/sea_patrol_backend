# План рефакторинга `sea_patrol_backend`

Дата: 2026-03-04  
Проект: `sea_patrol_backend` (Spring Boot + WebFlux + Spring Security + JWT, WebSocket `/ws/game`, in-memory users)

## 0) Цели и принципы

### Цели (результат)
- Обновить платформу до актуальной: **Spring Boot 4** (+ согласованные версии Spring Framework/Security/Reactor).
- Снизить техдолг вокруг **security/JWT**, **WebSocket**, **reactive-потоков**, **конфигурации**, **тестов**.
- Зафиксировать и поддерживать контракты (REST + WebSocket) в `ai-docs/API_INFO.md`, а архитектуру/структуру — в `ai-docs/PROJECT_INFO.md`.

### Принципы выполнения
- Не ломать протокол/контракты без явного пункта breaking change.
- Сохранять reactive-подход: **не добавлять `block()`/`toFuture().get()`** в WebFlux-потоках.
- Любое изменение security сопровождается проверкой `publicRoutes` в `src/main/java/ru/sea/patrol/config/WebSecurityConfig.java`.
- Любое изменение WebSocket-протокола сопровождается синхронным обновлением:
  - `src/main/java/ru/sea/patrol/ws/protocol/MessageType.java`
  - DTO из `src/main/java/ru/sea/patrol/ws/protocol/dto/*`
  - `src/main/java/ru/sea/patrol/ws/game/GameWebSocketHandler.java`
  - документа `ai-docs/API_INFO.md`
  - (после этапа 2 со структурным рефакторингом — обновить пути в этом документе)

### Набор “минимальной проверки” для каждого этапа
- `.\gradlew.bat test` (или явно зафиксировать, почему не запускалось).
- Auth:
  - `POST /api/v1/auth/signup`
  - `POST /api/v1/auth/login`
  - запрос к защищенному HTTP маршруту без токена → `401`
- WebSocket:
  - подключение к `ws://localhost:8080/ws/game?token=<jwt>` с валидным токеном

---

## CDXTSK: список задач (в порядке выполнения)

Ниже — список задач, которыми удобно управлять рефакторингом. Каждая задача должна заканчиваться зелёной сборкой и прогоном тестов.

1. **CDXTSK-1** — Включить и стабилизировать `ApplicationTests` + тестовый профиль (`src/test/resources/application.yaml`).
2. **CDXTSK-2** — REST integration-тесты auth (`/signup`, `/login`) через `WebTestClient`.
3. **CDXTSK-3** — Добавить защищенный endpoint “кто я” (`/api/v1/auth/me` или аналог) + тесты `401/200`.
4. **CDXTSK-4** — WebSocket smoke-тест handshake для `/ws/game?token=...` (валидный JWT).
5. **CDXTSK-5** — Box2D test harness (инициализация, natives, фиксированный timestep, epsilon).
6. **CDXTSK-6** — Physics-тесты `PlayerShipInstance` (тяга/поворот/демпфинг).
7. **CDXTSK-7** — Physics-тесты `GameRoom` (start/stop, многократные циклы, отсутствие исключений).
8. **CDXTSK-8** — Быстрые unit-тесты без Box2D (chat, сериализация ws сообщений, JWT).
9. **CDXTSK-9** — Структура пакетов P1: `ws/*` (перенос `GameWebSocketHandler`, `MessageType`, ws DTO).
10. **CDXTSK-10** — Структура пакетов P2: `auth/*` (controller + dto + security).
11. **CDXTSK-11** — Структура пакетов P3: `user/*` (entity + repo + service + mapper).
12. **CDXTSK-12** — Структура пакетов P4: `error/*` (exceptions + web error handler).
13. **CDXTSK-13** — Поднять Spring Boot до последнего `3.5.x` (patch) и удержать тесты зелеными.
14. **CDXTSK-14** — Перейти на Spring Boot `4.x` (сборка/старт/тесты).
15. **CDXTSK-15** — Прогнать `spring-boot-properties-migrator`, исправить конфиг, удалить мигратор.
16. **CDXTSK-16** — Убрать `jwt.secret` из репозитория, перейти на env/профили, обновить документацию запуска.
17. **CDXTSK-17** — Модернизировать JWT (обновить `jjwt` или перейти на Nimbus), поправить `JwtUtil`/converter, закрыть edge-cases.
18. **CDXTSK-18** — WebSocket lifecycle: убрать ручные `subscribe()`, сделать cleanup композиционным + `ObjectMapper` как bean.
19. **CDXTSK-19** — Игровой цикл: lifecycle scheduler, утечки, backpressure политика + тестовые сценарии.
20. **CDXTSK-20** — DTO validation (`@Valid`) + единый формат ошибок + тесты.
21. **CDXTSK-21** — Синхронизировать `ai-docs/*` и сделать финальный audit чеклист.

## 1) Тестовый контур до рефакторинга (обязательный первый шаг)

Задача этого этапа — **зафиксировать текущее поведение** и создать “страховочную сетку”, чтобы дальнейший рефакторинг (включая upgrade Spring Boot) был управляемым, а не ручным QA.

Текущее состояние:
- Есть `src/test/java/ru/sea/patrol/ApplicationTests.java`, но тест фактически выключен (аннотация `@Test` закомментирована).

### 1.1 Включить и стабилизировать базовый smoke-тест контекста (CDXTSK-1)
- Сделать `ApplicationTests` реально исполняемым:
  - вернуть `@Test` у `contextLoads()` или заменить на минимальный осмысленный smoke (например, старт контекста + проверка, что поднялся `WebTestClient`).
- Добавить профиль `test` (без секретов и внешних зависимостей):
  - тестовый `jwt.secret`/expiration/issuer задавать через `src/test/resources/application.yaml` (явно “dummy”) или через env в Gradle-таске.
- Убедиться, что тесты не зависят от `static/assets` и не пишут файлы на диск.

Критерий готовности: `.\gradlew.bat test` выполняет хотя бы 1 тест и он стабилен.

### 1.2 Добавить интеграционные тесты REST (WebFlux) через `WebTestClient` (CDXTSK-2)
Минимальный набор, который покрывает регрессии рефакторинга:
- `POST /api/v1/auth/signup`:
  - happy-path: `200 OK`, в ответе `username`;
  - bad input (после добавления `@Valid`) → `400` в едином формате ошибок.
- `POST /api/v1/auth/login`:
  - happy-path: `200 OK`, `token`, `issuedAt`, `expiresAt`;
  - invalid username/password → `401` + корректный формат ошибки (как задокументировано в `ai-docs/API_INFO.md`).

Рекомендуемая организация тестов:
- `src/test/java/ru/sea/patrol/auth/AuthControllerTest.java`
- общие утилиты/фикстуры: `src/test/java/ru/sea/patrol/testsupport/*`

### 1.3 Зафиксировать контракт security (401/403) “в тестах, а не в голове” (CDXTSK-3)
Проблема сейчас: в проекте почти нет защищенных REST-эндпоинтов кроме “anyExchange().authenticated()”, и тесту нужен стабильный защищенный URL.

План:
- Добавить небольшой защищенный эндпоинт “проверки токена”, полезный и в продукте:
  - например `GET /api/v1/auth/me` или `GET /api/v1/user/me`, который возвращает текущий username из `ReactiveSecurityContextHolder`.
- Важно: endpoint **не** добавлять в `publicRoutes` — он должен быть защищенным по умолчанию.
- Ответ сделать максимально простым и стабильным (DTO уровня API), например:
  - `{"username":"user1"}` (+ при необходимости `roles`).
- Тесты:
  - без токена → `401`;
  - с валидным токеном → `200` и `username`.

Критерий готовности: security-поведение проверяется автотестом, а не ручной проверкой.

### 1.4 Smoke-тест WebSocket handshake (`/ws/game`) с валидным JWT (CDXTSK-4)
План:
- Поднять приложение в тестовом контексте (random port).
- Получить JWT через `POST /api/v1/auth/login`.
- Подключиться к `ws://localhost:{port}/ws/game?token=<jwt>`.
- Проверить минимум:
  - соединение устанавливается;
  - приходит хотя бы одно корректно сериализованное сообщение сервера:
    - JSON-объект вида `{ "type": "...", "payload": ... }`;
    - `type` ∈ `MessageType`.
- Дополнительно (очень желательно, если тест не станет flaky):
  - подключение **без** `token` должно завершаться отказом (ожидаемый статус/ошибка зависит от реализации handshake в WebFlux Security).
- Техническая реализация (подсказка для задачи):
  - использовать `WebSocketClient` (например `ReactorNettyWebSocketClient`) и `ObjectMapper` для парсинга первого сообщения;
  - добавить таймаут ожидания первого сообщения (например 1–3 секунды), затем корректно закрывать сессию.

Критерий готовности: после апгрейдов/рефакторинга гарантированно не “сломали” handshake и формат сообщений.

### 1.5 Тесты физики (Box2D/LibGDX) — обязательно (CDXTSK-5, CDXTSK-6, CDXTSK-7)
Требование: физику **нужно** тестировать автотестами (регрессии в силах/демпфинге/степинге быстро ломают геймплей).
Ориентир по стилю сценариев: `raw/BodyForceTest.java` (управляемая симуляция в `World` с фиксированным timestep и проверками).

План:
- **CDXTSK-5 — Harness для Box2D/LibGDX:**
  - в `@BeforeAll` явно дернуть `com.badlogic.gdx.physics.box2d.Box2D.init()` (если потребуется для загрузки natives);
  - убедиться, что `gdx-box2d-platform:natives-desktop` попадает в `testRuntimeClasspath` (если нет — добавить отдельной зависимостью `testRuntimeOnly(...)`);
  - фиксированный timestep (например `1/60f`) и фиксированное число шагов;
  - сравнения float-значений через epsilon (`assertEquals(expected, actual, eps)`).
  - дисциплина ресурсов: `World.dispose()`, `Shape.dispose()` всегда в `finally`/`@AfterEach`.
  - стабильность: для physics-тестов отключить параллельное выполнение (или запускать одним fork), если будут падения из-за JNI/ресурсов.
- Выделить пакет тестов физики:
  - `src/test/java/ru/sea/patrol/game/physics/*`
  - пометить как `@Tag("physics")`, чтобы при необходимости запускать отдельно.
- **CDXTSK-6 — Physics-тесты `PlayerShipInstance`:**
  - при `input.up=true` скорость/смещение вдоль курса растёт после N шагов;
  - при `input.left/right` меняется `orientation` (угол) после N шагов;
  - демпфинг приводит к затуханию скорости при `thrust=0`;
  - edge-case: `input=null` не падает и не применяет силу.
- **CDXTSK-7 — Physics-тесты `GameRoom` (минимально и стабильно):**
  - `start()` → создает `World`, выставляет `started=true`, не бросает исключения;
  - `stop()` → освобождает `World`, выставляет `started=false`, не бросает исключения;
  - многократные циклы `start/stop` не приводят к ошибкам/утечкам (минимум: не растёт число активных rooms в `GameService` при тестовом сценарии).
- Не писать файлы из тестов (CSV/логи) как часть “нормального” прогона:
  - если нужен экспорт статистики — делать это в отдельном `@Disabled` тесте или под системным флагом.

Критерий готовности: `.\gradlew.bat test` включает хотя бы 2–3 physics-теста и они стабильны на целевых ОС/архитектурах.

### 1.6 Юнит-тесты “чистой” логики (без Box2D) (CDXTSK-8)
Параллельно с physics-тестами нужны быстрые тесты без нативной физики:
- `ChatService`:
  - join/leave групп, рассылка сообщений, личные каналы;
  - проверять через `StepVerifier`/подписку на `Flux`.
- сериализация/десериализация сообщений WebSocket:
  - входящий формат `[type, payload]` → `MessageInput`;
  - исходящий формат `{type,payload}`.
- `JwtUtil`:
  - генерация токена, проверка expiration, корректная обработка “битого” токена (должен быть `401`, не `500`).

---

## 2) Анализ текущей структуры пакетов и целевая организация (best practices)

### 2.1 Что есть сейчас (фактическая структура)
Текущее дерево пакетов под `src/main/java/ru/sea/patrol`:
- `config` — `WebSecurityConfig`, `WebSocketConfig`
- `controller` — `AuthController`, отдача SPA
- `dto/auth`, `dto/websocket`
- `entity` — `UserEntity`, `UserRole`
- `errorhandling` — web error handler/attributes
- `exception` — `ApiException`, `AuthException`, `UnauthorizedException`
- `handler` — `GameWebSocketHandler`
- `mapper` — `UserMapper`
- `repository` — `InMemoryUserRepository`, `UserRepository`
- `security` — JWT + reactive auth
- `service/chat`, `service/game`, `service/UserService`
- `MessageType.java` в корне пакета

### 2.2 Замечания по неймингу и “склеенным” зонам ответственности
- `handler` слишком общее имя: по факту это `websocket`/`ws` слой (лучше назвать явно).
- `errorhandling` и `exception` разнесены и названы по-разному (длинное имя пакета + разный стиль); обычно объединяют под `error`/`api.error`.
- `dto/*` как глобальная “свалка” со временем разрастается; практичнее хранить DTO ближе к фиче/транспорту (например, `auth.api.dto`, `ws.protocol.dto`).
- `entity` сейчас фактически только “user-domain”, поэтому логичнее `user.domain`.
- `service/UserService` рядом с `service/chat` и `service/game` — это признак, что пакет `service` смешивает фичи.
- `MessageType` лежит в корне — по смыслу это часть **WebSocket-протокола** (лучше рядом с `MessageInput/Output`).

### 2.3 Рекомендуемая целевая структура (ориентир)
Для монолита без БД и без модульности по Gradle оптимально **feature-first** (по доменам), оставляя “общие” части отдельно.

Целевой ориентир (пример):
```text
ru.sea.patrol
  SeaPatrolApplication

  app
    config
      CorsConfig
      JacksonConfig

  auth
    api
      AuthController
      dto
        AuthRequestDto
        AuthResponseDto
        UserRegistrationDto
    service
      AuthService (или переименование ReactiveSecurityManager)
    security
      JwtUtil
      JwtAuthenticationConverter
      ReactiveSecurityManager
      TokenDetails
      TokenVerificationResult

  user
    domain
      UserEntity
      UserRole
    repository
      UserRepository
      InMemoryUserRepository
    service
      UserService
    mapper
      UserMapper

  ws
    config
      WebSocketConfig
    game
      GameWebSocketHandler
    protocol
      MessageType
      dto
        MessageInput
        MessageOutput
        (прочие websocket DTO)

  chat
    domain
      ChatGroup
      ChatUser
    service
      ChatService

  game
    domain
      Player
      PlayerShipInstance
      Wind
      GameRoom
    service
      GameService

  error
    api
      AppErrorAttributes
      AppErrorWebExceptionHandler
    domain
      ApiException
      AuthException
      UnauthorizedException
```

Почему так:
- проще навигация: “ищу auth” → всё в `auth/*`, “ищу ws протокол” → всё в `ws/protocol/*`;
- меньше циклических зависимостей между пакетами;
- DTO не превращаются в глобальную помойку;
- `MessageType` и websocket DTO держатся вместе как контракт.

### 2.4 Стратегия переезда (чтобы не утонуть в rename-ах)
Порядок (инкрементально, с тестами):
1. Сначала этап 1 (тесты) — иначе refactor становится “на ощупь”.
2. Переименовать/переместить **самые очевидные** пакеты с минимумом связей:
   - `handler` → `ws.game`
   - `MessageType` → `ws.protocol`
3. Затем “пучками” по фичам:
   - `controller + dto/auth + security` → `auth/*`
   - `entity + repository + service/UserService + mapper` → `user/*`
4. Только потом трогать `errorhandling/exception` (это затрагивает обработку ошибок по всему приложению).

Критерий готовности: после каждого шага тесты и сборка проходят, `ai-docs/*` синхронизированы при изменении публичных контрактов.

Разбивка на задачи:
- **CDXTSK-9 (P1 / ws):**
  - перенести `src/main/java/ru/sea/patrol/handler/GameWebSocketHandler.java` → `src/main/java/ru/sea/patrol/ws/game/GameWebSocketHandler.java`;
  - перенести `src/main/java/ru/sea/patrol/MessageType.java` и DTO websocket → `src/main/java/ru/sea/patrol/ws/protocol` (и под-пакеты);
  - обновить импорты и `ai-docs/API_INFO.md`, если документ ссылается на классы/пути.
- **CDXTSK-10 (P2 / auth):**
  - перенести `controller/AuthController` + `dto/auth/*` + `security/*` в `ru.sea.patrol.auth/*` (как в целевой структуре);
  - привести имена: `ReactiveSecurityManager.login(...)` либо переименовать в `AuthService`, либо оставить, но не смешивать “security” и “business”.
- **CDXTSK-11 (P3 / user):**
  - перенести `entity/*`, `repository/*`, `service/UserService`, `mapper/*` в `ru.sea.patrol.user/*`.
- **CDXTSK-12 (P4 / error):**
  - объединить `errorhandling` + `exception` в `ru.sea.patrol.error/*` (предложенная структура);
  - отдельно проверить, что security entrypoints всё ещё возвращают ожидаемые статусы/тела.

Техника выполнения (для каждой задачи CDXTSK-9..12):
- Делать перенос IDE-рефакторингом (Move package), чтобы не сломать импорты.
- После каждого переноса:
  - `.\gradlew.bat test`;
  - быстрый smoke запуск (по необходимости);
  - поправить ссылки на пути в `raw/refactor.md` и в `ai-docs/*`, если они “захардкожены”.


## 3) Переход на Spring Boot 4

Текущее состояние: `org.springframework.boot` **3.5.6** (`build.gradle.kts`), Gradle wrapper **8.14.3**, Java toolchain **25**.

> Важно: Spring Boot 4 документация рекомендует использовать Java SDK **17+** и при апгрейде опираться на release notes и `spring-boot-properties-migrator` на время миграции.

### 3.1 Подготовка (до изменения версии) (CDXTSK-13)
- Зафиксировать “точку отсчёта”:
  - собрать список публичных маршрутов (`publicRoutes`) и ожидаемое поведение 401/403;
  - зафиксировать фактический WebSocket формат сообщений (он уже описан в `ai-docs/API_INFO.md`).
- Обновить Spring Boot в пределах текущей ветки (patch) перед скачком:
  - поднять до **последнего доступного 3.5.x** (уменьшает риск прыжка через промежуточные фиксы).
- Прогнать `.\gradlew.bat build` и сохранить лог (на случай регрессий).

Критерий готовности: проект собирается и стартует на актуальном 3.5.x, функциональные проверки выше проходят.

### 3.2 Обновление Gradle-плагина Spring Boot до 4.x (CDXTSK-14)
- В `build.gradle.kts`:
  - обновить `id("org.springframework.boot") version "4.x.y"`;
  - при необходимости обновить `io.spring.dependency-management` до совместимой версии (оставить как есть, если совместимо).
- Убедиться, что wrapper Gradle соответствует требованиям (у нас `gradle-8.14.3` — вероятно ок).
- Собрать проект: `.\gradlew.bat clean build`.

Критерий готовности: проект компилируется/собирается, без ручного форсирования версий Spring Framework.

### 3.3 Временная миграция конфигурационных свойств (CDXTSK-15)
- На время апгрейда добавить:
  - `runtimeOnly("org.springframework.boot:spring-boot-properties-migrator")`
- Запустить приложение и устранить предупреждения мигратора.
- После стабилизации удалить `spring-boot-properties-migrator` из зависимостей.

Критерий готовности: приложение стартует без предупреждений о deprecated/renamed properties.

### 3.4 Починка компиляции и “пакетных” несовместимостей (CDXTSK-14)
Типовые места риска для Spring Boot 4:
- `javax.*` vs `jakarta.*` (если обновление поднимет baseline Jakarta);
- старые библиотеки, которые тянут `javax.xml.bind` и др.

Действия в этом проекте:
- Пересмотреть зависимости JWT:
  - сейчас `io.jsonwebtoken:jjwt:0.9.1` + `javax.xml.bind:jaxb-api:2.3.1` — это явный legacy-набор.
  - спланировать обновление jjwt на актуальную линейку (см. этап 4) и убрать “костыль” `jaxb-api`, если больше не нужен.

Критерий готовности: нет `ClassNotFoundException`/`NoSuchMethodError` на старте, тесты хотя бы компилируются.

### 3.5 Аудит Spring Security (WebFlux) после апгрейда (CDXTSK-14)
- Проверить `src/main/java/ru/sea/patrol/config/WebSecurityConfig.java`:
  - что DSL и используемые классы (`SecurityWebFilterChain`, `AuthenticationWebFilter`, `ServerHttpSecurity`) остаются совместимыми;
  - что `publicRoutes` не расширились “случайно”.
- Проверить конвертацию токена:
  - `src/main/java/ru/sea/patrol/auth/security/JwtAuthenticationConverter.java` (GET `/ws/*` берёт `token` из query).

Критерий готовности:
- `POST /api/v1/auth/login` возвращает JWT и дальнейшие запросы проходят.
- Без токена защищённые HTTP маршруты дают `401`.
- Подключение к `/ws/game` с токеном работает.

### 3.6 Аудит WebFlux/WebSocket поведения и реактивности (CDXTSK-14)
- Проверить, что в `src/main/java/ru/sea/patrol/ws/game/GameWebSocketHandler.java`:
  - нет ручных `subscribe()` в `doFinally` (желательно заменить на реактивное “then/usingWhen”-подобное завершение);
  - инициализация `joinRoom/startRoom` не создаёт гонок/повторов при переподключениях.
- Добавить/расширить минимальные автотесты (см. этап 1), чтобы апгрейд платформы не превращался в ручной чеклист.

Критерий готовности: несколько переподключений WebSocket подряд не приводят к утечкам потоков/комнат/планировщиков.

---

## 4) Модернизация JWT и security-слоя (после Boot 4)

### 4.1 Устранить хранение секрета в репозитории (CDXTSK-16)
Текущее состояние: `src/main/resources/application.yaml` содержит фиксированный `jwt.secret`.

План:
- Перенести `jwt.secret` в переменные окружения/профили:
  - например, `JWT_SECRET` (или `SPRING_APPLICATION_JSON`) для прод/стейдж.
- Добавить “dev-only” значение через отдельный профиль (если нужно), но **не** коммитить реальный секрет.
- Обновить `ai-docs/PROJECT_INFO.md` (раздел рисков) и инструкции запуска.

Критерий готовности: приложение стартует без секрета в репозитории; CI/локальный запуск описан.

### 4.2 Обновить `jjwt` и привести код подписи/валидации к современному API (CDXTSK-17)
Риски текущей реализации:
- `jjwt 0.9.1` устарел, использует старые API, требует `jaxb-api` и часто конфликтует с современными зависимостями.
- `JwtAuthenticationConverter` делает `substring` без проверки, что значение начинается с `Bearer `.
- Для WebSocket query `token` возможен кейс `null` → строка `"Bearer "` → пустой токен.

План:
- Принять решение по стеку JWT (выбрать один вариант и зафиксировать):
  - **Вариант A (минимальный риск):** оставить `jjwt`, но обновить до актуальной линейки и перейти на современный API (модули `jjwt-api/impl/jackson`).
  - **Вариант B (spring-way):** перейти на `spring-security-oauth2-jose` и `NimbusReactiveJwtDecoder`/`NimbusJwtEncoder` для HS256.
- Обеспечить обратную совместимость токенов (если требуется клиентами):
  - алгоритм подписи (сейчас HS256), issuer, subject, claims (`role`) — должны остаться совместимыми или быть мигрированы контролируемо.
- Привести `JwtUtil` к безопасной работе с ключом:
  - использовать `Key`/`SecretKey` и явную работу с base64 (если `jwt.secret` хранится как base64);
  - убрать “double-base64” (сейчас фактически base64 применяется поверх raw bytes строки).
- В `JwtAuthenticationConverter`:
  - корректно обрабатывать отсутствие/невалидность заголовка/параметра;
  - не падать на `substring`, если нет `Bearer ` (проверка префикса до `substring`);
  - для `/ws/*` при отсутствии `token` возвращать empty (чтобы отработал `401`), а не создавать “Bearer null”.
- Закрыть тестами edge-cases (в рамках CDXTSK-2/3/4/8):
  - пустой токен / “Bearer ” / мусорная строка → `401`;
  - истекший токен → `401`;
  - валидный токен → `200` на `/me` и успешный WS handshake.

Критерий готовности: 401 при пустом/битом токене, без 500 и без ошибок парсинга строки.

### 4.3 Стабилизировать обработку ошибок security (опционально) (CDXTSK-17, опционально)
Сейчас `authenticationEntryPoint`/`accessDeniedHandler` возвращают только статус без JSON-тела.

План (если нужно единообразие):
- Согласовать формат security-ошибок с `src/main/java/ru/sea/patrol/error/*` и `ai-docs/API_INFO.md`.
- При необходимости вернуть JSON `{ errors: [...] }` и для security ошибок (не ломая клиентов).

---

## 5) Приведение реактивных потоков и жизненного цикла WebSocket к “чистому” WebFlux

Цель: убрать скрытые side-effects, которые сложно тестировать и которые приводят к утечкам.

### 5.1 `GameWebSocketHandler`: устранить ручной `subscribe()` и централизовать cleanup (CDXTSK-18)
Текущее:
- `doFinally(sig -> cleanup.subscribe())` — ручная подписка внутри реактивного пайплайна.

План:
- Переписать финализацию в композиции `Mono` (например: `session.send(outbound).and(input).then(cleanup)`).
- Сделать cleanup идемпотентным (повторный вызов безопасен).

Критерий готовности: при закрытии WS-сессии cleanup гарантированно вызывается ровно 1 раз.

### 5.2 `ObjectMapper` как Spring Bean (CDXTSK-18)
Текущее:
- `new ObjectMapper()` создаётся в `GameWebSocketHandler`, `GameService`, `ChatService`, `GameRoom`.

План:
- Инжектить `ObjectMapper` как bean (единая конфигурация Jackson).

Критерий готовности: единые настройки сериализации/десериализации, меньше аллокаций.

---

## 6) Рефакторинг игрового цикла (без изменения протокола)

### 6.1 Управление потоками/планировщиками комнаты (CDXTSK-19)
Текущее:
- `GameRoom` создаёт `Executors.newSingleThreadScheduledExecutor()` на комнату.

План:
- Явно управлять жизненным циклом scheduler:
  - гарантировать `stop()` при пустой комнате/выходе последнего игрока;
  - добавить защиту от двойного `start()` и конкурирующих `startRoom()` при переподключениях.
- Опционально перейти на Reactor `Scheduler`/`Flux.interval` (если это упростит контроль и тестируемость), сохранив Box2D на выделенном потоке.

Критерий готовности: при N циклах join/leave/stop нет роста активных потоков.

### 6.2 Backpressure и “медленные клиенты” (CDXTSK-19)
Текущее:
- `Sinks.many().multicast().onBackpressureBuffer()` может накапливать сообщения при медленном клиенте.

План:
- Определить допустимую политику:
  - либо ограниченный буфер + drop (для `UPDATE_GAME_STATE`),
  - либо rate limit/семплинг updates (например, не чаще X/сек).
- Зафиксировать это в `ai-docs/API_INFO.md` как поведение сервера.

---

## 7) DTO-валидация и единый контракт ошибок (REST)

Текущее (из `ai-docs/PROJECT_INFO.md`/`API_INFO.md`):
- DTO регистрации/логина не валидируются через `@Valid`.

План:
- Добавить аннотации в DTO (минимум: `@NotBlank`, `@Size`, `@Email` где нужно).
- В `AuthController` включить `@Valid` и покрыть кейсы тестами.
- Согласовать формат ошибок в `ai-docs/API_INFO.md`.

Задача:
- **CDXTSK-20** — внедрить `@Valid` и обеспечить совместимость контракта ошибок.

Критерий готовности: некорректный input даёт предсказуемый `400` с телом в едином формате.

---

## 8) Документация и “сверка source of truth”

План:
- После этапа 3 (Boot 4) обновить версии и требования в:
  - `ai-docs/PROJECT_INFO.md`
- После этапов 4–7 обновить:
  - `ai-docs/API_INFO.md` (если менялось поведение/контракты)
- Договориться о правилах версионирования WebSocket-протокола (если планируется breaking change):
  - например, `protocolVersion` в сообщениях или отдельный `HELLO`/`CAPABILITIES`.

Задача:
- **CDXTSK-21** — синхронизация документации и финальный audit.

---

## 9) Рекомендованный порядок выполнения (срезами)

1. Тесты (CDXTSK-1 → CDXTSK-8): сначала фиксация поведения, включая physics.  
2. Структура пакетов (CDXTSK-9 → CDXTSK-12): инкрементально, после каждого шага тесты зелёные.  
3. Платформа (CDXTSK-13 → CDXTSK-15): апгрейд Spring Boot до 4.x и стабилизация конфигов.  
4. Security/JWT (CDXTSK-16 → CDXTSK-17): убрать секреты из репо и модернизировать токены.  
5. WebSocket/WebFlux (CDXTSK-18): lifecycle cleanup и единый `ObjectMapper`.  
6. Game loop (CDXTSK-19): lifecycle scheduler + backpressure политика.  
7. DTO validation + ошибки (CDXTSK-20): контракты и тесты.  
8. Документация и финальный audit (CDXTSK-21).
