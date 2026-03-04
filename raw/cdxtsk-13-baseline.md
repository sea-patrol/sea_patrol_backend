# CDXTSK-13 — Baseline (подготовка к апгрейду Spring Boot 4)

Дата: 2026-03-04

## Цель
Перед переходом на Spring Boot 4 подняться до последнего patch-релиза в ветке `3.5.x`, зафиксировать текущее поведение и иметь воспроизводимый лог сборки.

## Текущее поведение/контракты (фиксируем как baseline)

### Security
- Публичные маршруты заданы в `src/main/java/ru/sea/patrol/config/WebSecurityConfig.java`:
  - `/`, `/game`, `/assets/**`, `/**.html`, `/**.svg`, `/**.glb`
  - `POST /api/v1/auth/signup`, `POST /api/v1/auth/login`
  - `/sw.js`, `/registerSW.js`, `/manifest.webmanifest`, `/workbox**`
- Защищённый “smoke” endpoint:
  - `GET /api/v1/auth/me`:
    - без токена → `401`
    - с токеном → `200 { "username": "..." }`

### WebSocket
- Endpoint: `/ws/game`
- Handshake: `ws://localhost:8080/ws/game?token=<jwt>`
- Smoke-тест: подключение с валидным токеном получает JSON-конверт `{type,payload}`.

### Test profile (без реальных секретов)
- `src/test/resources/application.yaml` содержит dummy `jwt.*` только для тестов.

## Версии (до / после CDXTSK-13)
- Было: Spring Boot Gradle plugin `3.5.6`
- Стало: Spring Boot Gradle plugin `3.5.11` (последний patch в `3.5.x` на 2026-03-04)

## Проверка
- `.\gradlew.bat clean build`
  - лог: `raw/logs/cdxtsk-13-clean-build-20260304-171203.log`
