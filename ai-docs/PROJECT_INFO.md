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
- Язык: `Java 24` (toolchain в Gradle).
- Framework: `Spring Boot 3.5.6`.
- Web: `spring-boot-starter-webflux`.
- Security: `spring-boot-starter-security`, JWT на `jjwt 0.9.1`.
- Mapping: `MapStruct 1.6.3`.
- Utility: `Lombok`.
- Игровая физика: `LibGDX 1.12.1`, `Box2D`, `gdx-ai`.
- Build: `Gradle` (`build.gradle.kts`).

## 3. Структура репозитория
- `src/main/java/ru/sea/patrol/SeaPatrolApplication.java` — точка входа.
- `src/main/java/ru/sea/patrol/config` — безопасность и WebSocket-маршрутизация.
- `src/main/java/ru/sea/patrol/controller` — REST auth и отдача SPA.
- `src/main/java/ru/sea/patrol/handler` — единый WebSocket handler.
- `src/main/java/ru/sea/patrol/service/chat` — чат-группы и сообщения.
- `src/main/java/ru/sea/patrol/service/game` — игровые комнаты, цикл обновления, игроки.
- `src/main/java/ru/sea/patrol/security` — JWT-проверка, authentication manager, converter.
- `src/main/java/ru/sea/patrol/repository` — in-memory репозиторий пользователей.
- `src/main/java/ru/sea/patrol/errorhandling` — единый JSON-формат ошибок для приложенческих исключений.
- `src/main/resources/application.yaml` — конфиг приложения и JWT.
- `src/main/resources/static` — собранные фронтенд-артефакты.
- `src/test/java/ru/sea/patrol` — тесты (на текущий момент минимальные).

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
- На регистрации нет явной серверной валидации DTO-аннотациями (`@Valid` не используется).
- Нет версионирования WebSocket-протокола; изменения формата сообщений требуют ручной синхронизации клиента/сервера.

## 6. Сборка и запуск
- Windows:
  - `.\gradlew.bat bootRun`
  - `.\gradlew.bat test`
  - `.\gradlew.bat build`
- Linux/macOS:
  - `./gradlew bootRun`
  - `./gradlew test`
  - `./gradlew build`

## 7. Риски и техдолг
- `application.yaml` содержит фиксированный JWT secret (для продакшена требуется внешняя конфигурация).
- Тестовое покрытие минимальное (фактически нет активных интеграционных/юнит тестов).
- Статика фронтенда хранится как build output; ручные правки в `static/assets` легко приводят к рассинхронизации.
