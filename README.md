# Sea Patrol Backend

Backend для мультиплеерной игры Sea Patrol на `Spring Boot WebFlux`.

## Что есть сейчас
- JWT-аутентификация (`signup/login`)
- WebSocket игровой канал (`/ws/game`)
- Игровые обновления в реальном времени
- Чат (глобальный/групповой/личный)
- In-memory хранилище пользователей (без БД)

## Быстрый старт
Требования: `Java 25`.

Windows:
```powershell
.\gradlew.bat bootRun
```

Linux/macOS:
```bash
./gradlew bootRun
```

Тесты:
```bash
./gradlew test
```

## API (кратко)
- `POST /api/v1/auth/signup`
- `POST /api/v1/auth/login`
- `GET /` и `GET /game` (отдача SPA)
- `WS /ws/game?token=<jwt>`

## Документация в репозитории
- Проект и архитектура: `ai-docs/PROJECT_INFO.md`
- API и контракты: `ai-docs/API_INFO.md`
- Правила для агентов: `AGENTS.md`, `QWEN.md`

