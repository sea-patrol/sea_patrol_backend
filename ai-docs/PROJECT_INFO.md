# PROJECT_INFO.md

## Назначение
Документ описывает текущую архитектуру и структуру `sea_patrol_backend` как source of truth для инженерных задач.

## 1. Общее описание
- Тип проекта: backend для мультиплеерной морской игры.
- Архитектура: reactive (`Spring WebFlux` + `Project Reactor`), real-time через WebSocket.
- Функции:
  - регистрация и логин пользователей;
  - JWT-аутентификация для защищенных маршрутов и WebSocket;
  - игровой цикл комнаты (физика, состояние игроков, ветер);
  - чат (глобальный, групповой, личный).

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
- `src/main/java/ru/sea/patrol/controller` — отдача SPA (`/`, `/game`).
- `src/main/java/ru/sea/patrol/auth` — REST auth (`/api/v1/auth/*`) + JWT/security компоненты.
- `src/main/java/ru/sea/patrol/user` — домен пользователей + in-memory репозиторий.
- `src/main/java/ru/sea/patrol/ws` — WebSocket handler `/ws/game` + протокол сообщений (MessageType + DTO).
- `src/main/java/ru/sea/patrol/service/chat` — чат-группы и сообщения.
- `src/main/java/ru/sea/patrol/service/game` — игровые комнаты, цикл обновления, игроки.
- `src/main/java/ru/sea/patrol/error` — единый JSON-формат ошибок для приложенческих исключений.
- `src/main/resources/application.yaml` — конфиг приложения и JWT.
- `src/main/resources/static` — собранные фронтенд-артефакты.
- `src/test/java/ru/sea/patrol` — тесты (есть REST/WebSocket интеграционные и physics-тесты Box2D).

## 4. Runtime-потоки
### 4.1 HTTP / Auth
- `POST /api/v1/auth/signup` создает пользователя в in-memory хранилище.
- `POST /api/v1/auth/login` валидирует учетные данные и возвращает JWT + timestamps.

### 4.2 Безопасность
- Публичные маршруты: `/`, `/game`, статика, `POST /api/v1/auth/signup`, `POST /api/v1/auth/login`.
- Остальные HTTP-маршруты требуют JWT в `Authorization: Bearer <token>`.
- Для WebSocket handshake (`GET /ws/...`) токен читается из query-параметра `token`.

### 4.3 WebSocket / Игра
- Endpoint: `/ws/game`.
- При подключении пользователя:
  - инициализируется чат-поток;
  - инициализируется игровой поток;
  - пользователь помещается в комнату `main`;
  - при первом подключении запускается игровой цикл комнаты.
- Частота обновлений комнаты: каждые ~100 мс (`ScheduledExecutorService`).

## 5. Доменные ограничения (текущее состояние)
- Пользователи и игровые сущности хранятся в памяти процесса.
- Предзаполненные пользователи в `InMemoryUserRepository`: `user1/user2/user3` с паролем `123456`.
- В auth DTO включена серверная валидация (`@Valid` + jakarta validation annotations) для `/api/v1/auth/signup` и `/api/v1/auth/login`.
- Нет версионирования WebSocket-протокола; изменения формата сообщений требуют ручной синхронизации клиента/сервера.

## 6. Сборка и запуск
- Для запуска требуется JWT secret (одна из переменных окружения):
  - `JWT_SECRET` — raw строка (рекомендуется >= 32 байта);
  - `JWT_SECRET_BASE64` — base64/base64url-байты (после декодирования >= 32 байта).

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
