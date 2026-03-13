# TASK-033 - Backend часть: движение парусника зависит от ветра

## Метаданные
- **ID:** `TASK-033`
- **Тип:** `feature`
- **Статус:** `Done`
- **Приоритет:** `High`
- **Дата создания:** `2026-03-12`
- **Автор:** `Codex`
- **Связанный roadmap item:** `Wave 4 / TASK-033`
- **Трек:** `Backend`
- **Depends on:** `TASK-031`

## Контекст
После `TASK-031` backend уже держал authoritative wind state, но движение корабля всё ещё было почти "моторным": `PlayerShipInstance` получал `wind` в `update(...)`, однако полностью игнорировал его при вычислении тяги.

## Цель
Перевести backend sailing movement на простую server-authoritative модель, где ускорение судна зависит от силы ветра, направления ветра и курса корабля.

## Source of Truth
- Код:
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/PlayerShipInstance.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/Wind.java`
  - `sea_patrol_backend/src/main/java/ru/sea/patrol/service/game/GameRoom.java`
- Тесты:
  - `sea_patrol_backend/src/test/java/ru/sea/patrol/game/physics/PlayerShipInstancePhysicsTest.java`
- Документация:
  - `sea_patrol_backend/ai-docs/PROJECT_INFO.md`
  - `sea_patrol_backend/ai-docs/API_INFO.md`
  - `sea_patrol_orchestration/PROJECTS_ORCESTRATION_INFO.md`
  - `sea_patrol_orchestration/ROADMAP-TASKS.md`

## Acceptance Criteria
- [x] Судно движется по-разному при попутном, боковом и встречном ветре.
- [x] Сила ветра влияет на ускорение.
- [x] Модель остаётся предсказуемой и покрыта backend physics tests.

## Scope
**Включает:**
- расчёт sail drive из room wind state и курса корабля;
- использование wind-dependent thrust внутри `PlayerShipInstance.update(...)`;
- тесты на `tailwind / beam / headwind` и на силу ветра;
- синхронизацию backend/orchestration docs.

**Не включает (out of scope):**
- сложную модель парусов и углов атаки;
- изменение transport contract;
- clockwise wind rotation policy;
- frontend HUD/UX по ветру.

## Технический подход
Модель сделана нарочито простой и устойчивой:
- корабль берёт текущий курс как forward-вектор;
- backend сравнивает этот курс с направлением ветра через `dot`;
- на основе alignment считается коэффициент `sail drive`;
- `beam reach` даёт лучший drive, `tailwind` помогает умеренно, `headwind` заметно режет эффективность;
- итоговая тяга также масштабируется силой ветра.

Это даёт предсказуемое поведение без попытки симулировать полноценную парусную аэродинамику.

## Проверки
### Автоматические проверки
| Репозиторий | Команда | Зачем | Статус |
|-------------|---------|-------|--------|
| `sea_patrol_backend` | `.\gradlew.bat test` | Проверить backend suite после wind-driven movement changes | `Passed` |

### Ручная проверка
- [x] `beam reach` быстрее `tailwind`
- [x] `tailwind` быстрее `headwind`
- [x] более сильный ветер даёт большую скорость при том же курсе

## QA / Review
### QA
- [x] Все acceptance criteria подтверждены
- [x] Backend tests проходят
- [x] Документация синхронизирована

**QA статус:** `Passed`

### Code Review
| Приоритет | Комментарий | Статус |
|-----------|-------------|--------|
| `Low` | Модель intentionally simplified; тонкая настройка sailing feel и clockwise wind policy остаются следующими задачами wave | `Resolved` |

**Review решение:** `Approve`

## Финализация
- [x] Backend movement теперь зависит от ветра
- [x] Tests проходят
- [x] Документация синхронизирована
- [x] Задача помечена как выполненная в roadmap
