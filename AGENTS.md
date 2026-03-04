# AGENTS.md

## Назначение
Этот файл задает рабочие правила для AI-агентов в `sea_patrol_backend`.

## Каноничные документы проекта
- Подробное описание проекта и структуры: `ai-docs/PROJECT_INFO.md`
- Текущее API (REST + WebSocket): `ai-docs/API_INFO.md`

При расхождениях между общими описаниями и кодом приоритет у кода, затем нужно обновить документы выше.

## Кратко о проекте
- Стек: `Spring Boot 3.5.6` + `WebFlux` + `Spring Security` + `JWT`.
- Язык/сборка: `Java 25`, `Gradle (Kotlin DSL)`.
- Реалтайм: WebSocket-обработчик игры на `/ws/game`.
- Игровая логика: `LibGDX + Box2D + gdx-ai`.
- Хранилище пользователей: in-memory (без БД).
- Статика фронтенда уже лежит в `src/main/resources/static`.

## Структура кода
- `src/main/java/ru/sea/patrol/config` — security + websocket конфигурация.
- `src/main/java/ru/sea/patrol/controller` — REST-эндпоинты (`/api/v1/auth/*`) и отдача SPA (`/`, `/game`).
- `src/main/java/ru/sea/patrol/handler` — WebSocket обработчик.
- `src/main/java/ru/sea/patrol/service` — бизнес-логика (`chat`, `game`).
- `src/main/java/ru/sea/patrol/security` — JWT и reactive auth.
- `src/main/resources/application.yaml` — настройки приложения и JWT.

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
   - проверять `publicRoutes` в `WebSecurityConfig`;
   - новые публичные маршруты добавлять осознанно, не расширять доступ по умолчанию.
3. При изменении WebSocket-протокола:
   - синхронно обновлять `MessageType`, входные/выходные DTO и обработку в `GameWebSocketHandler`;
   - не ломать формат сообщений без явной задачи на breaking change.
4. Не редактировать сгенерированные/собранные фронтенд-артефакты в `static/assets` вручную, если задача не про фронтенд-сборку.
5. Не добавлять секреты в код. Для новых секретов использовать конфигурацию через переменные окружения/профили.
6. При изменении контрактов API обязательно синхронизировать:
   - `ai-docs/API_INFO.md` для REST/WebSocket контрактов;
   - `ai-docs/PROJECT_INFO.md`, если изменилась архитектура/структура.

## Минимальная проверка перед сдачей
1. `gradlew test` проходит (или явно описана причина, почему не запускался).
2. Для изменений auth/security проверить:
   - `POST /api/v1/auth/signup`
   - `POST /api/v1/auth/login`
   - доступ к защищенному маршруту без токена (должен быть `401`).
3. Для изменений WebSocket проверить подключение к `/ws/game` с валидным токеном.

## Ограничения и важные замечания
- Текущий репозиторий содержит только один базовый тест (`ApplicationTests`) и он фактически отключен; при изменениях логики желательно добавлять тесты.
- В проекте нет персистентного слоя БД; изменения репозиториев влияют на in-memory поведение.
- При крупных изменениях сначала фиксировать контракт (DTO/протокол/эндпоинты), потом реализацию.
