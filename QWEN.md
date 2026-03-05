# QWEN.md

## Назначение
Этот файл синхронизирован с `AGENTS.md` и задает рабочие правила для AI-агентов в `sea_patrol_backend`.

## Каноничные документы проекта
- Подробное описание проекта и структуры: `ai-docs/PROJECT_INFO.md`
- Текущее API (REST + WebSocket): `ai-docs/API_INFO.md`

При расхождениях между общими описаниями и кодом приоритет у кода, затем нужно обновить документы выше.

## Кратко о проекте
- Стек: `Spring Boot 4.0.3` + `WebFlux` + `Spring Security` + `JWT`.
- Язык/сборка: `Java 25`, `Gradle (Kotlin DSL)`.
- Реалтайм: WebSocket-обработчик игры на `/ws/game`.
- Игровая логика: `LibGDX + Box2D + gdx-ai`.
- Хранилище пользователей: in-memory (без БД).
- Статика фронтенда уже лежит в `src/main/resources/static`.

## Структура кода
- `src/main/java/ru/sea/patrol/config` — Spring Security (WebFlux) + WebSocket конфигурация.
- `src/main/java/ru/sea/patrol/controller` — отдача SPA (`/`, `/game`).
- `src/main/java/ru/sea/patrol/auth/api` — REST auth (`/api/v1/auth/*`).
- `src/main/java/ru/sea/patrol/auth/security` — JWT и reactive auth-компоненты.
- `src/main/java/ru/sea/patrol/user` — домен пользователей + in-memory репозиторий.
- `src/main/java/ru/sea/patrol/service` — бизнес-логика (`chat`, `game`).
- `src/main/java/ru/sea/patrol/ws/game` — WebSocket-обработчик игры на `/ws/game`.
- `src/main/java/ru/sea/patrol/ws/protocol` — WebSocket протокол (тип сообщения + DTO).
- `src/main/java/ru/sea/patrol/error` — единый JSON-формат ошибок.
- `src/main/resources/application.yaml` — настройки приложения и JWT.
- `src/main/resources/static` — собранные фронтенд-артефакты.

## Команды разработки
- Windows:
  - `.\gradlew.bat bootRun`
  - `.\gradlew.bat test`
  - `.\gradlew.bat build`
- Linux/macOS:
  - `./gradlew bootRun`
  - `./gradlew test`
  - `./gradlew build`

## Обязательные правила изменений
1. Сохранять reactive-подход: не добавлять блокирующие вызовы (`block()`, `toFuture().get()` и т.п.) в WebFlux-потоках.
2. При изменении security:
   - проверять `publicRoutes` в `src/main/java/ru/sea/patrol/config/WebSecurityConfig.java`;
   - новые публичные маршруты добавлять осознанно, не расширять доступ по умолчанию.
3. При изменении WebSocket-протокола:
   - синхронно обновлять `src/main/java/ru/sea/patrol/ws/protocol/MessageType.java`, DTO из `src/main/java/ru/sea/patrol/ws/protocol/dto/*` и обработку в `src/main/java/ru/sea/patrol/ws/game/GameWebSocketHandler.java`;
   - не ломать формат сообщений без явной задачи на breaking change.
4. Не редактировать сгенерированные/собранные фронтенд-артефакты в `src/main/resources/static/assets` вручную, если задача не про фронтенд-сборку.
5. Не добавлять секреты в код. Для новых секретов использовать конфигурацию через переменные окружения/профили.
6. При изменении контрактов API обязательно синхронизировать:
   - `ai-docs/API_INFO.md` для REST/WebSocket контрактов;
   - `ai-docs/PROJECT_INFO.md`, если изменилась архитектура/структура.

## Минимальная проверка перед сдачей
1. `.\gradlew.bat test` проходит (или явно описана причина, почему не запускался).
2. Для изменений auth/security проверить:
   - `POST /api/v1/auth/signup`
   - `POST /api/v1/auth/login`
   - доступ к защищенному маршруту без токена (должен быть `401`).
3. Для изменений WebSocket проверить подключение к `/ws/game` с валидным токеном.

## Ограничения и важные замечания
- В проекте мало тестов; при изменениях логики желательно добавлять/расширять тесты (особенно для auth/security и WebSocket).
- В проекте нет персистентного слоя БД; изменения репозиториев влияют на in-memory поведение.
- При крупных изменениях сначала фиксировать контракт (DTO/протокол/эндпоинты), потом реализацию.
